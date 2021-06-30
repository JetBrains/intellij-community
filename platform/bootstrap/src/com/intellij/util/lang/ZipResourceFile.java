// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

@SuppressWarnings("SuspiciousPackagePrivateAccess")
public final class ZipResourceFile implements ResourceFile {
  private static final int MANIFEST_HASH_CODE = 0x4099_fd89;  // = Murmur3_32Hash.MURMUR3_32.hashString(JarFile.MANIFEST_NAME)

  private final ImmutableZipFile zipFile;

  public ZipResourceFile(@NotNull Path file) {
    ZipFilePool pool = ZipFilePool.POOL;
    try {
      if (pool == null) {
        zipFile = ImmutableZipFile.load(file);
      }
      else {
        Object zipFile = pool.loadZipFile(file);
        this.zipFile = (ImmutableZipFile)zipFile;
      }
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void processResources(@NotNull String dir,
                               @NotNull Predicate<? super String> nameFilter,
                               @NotNull BiConsumer<? super String, ? super InputStream> consumer) throws IOException {
    int minNameLength = dir.length() + 2;
    for (ImmutableZipEntry entry : zipFile.getEntries()) {
      String name = entry.getName();
      if (name.length() >= minNameLength && name.startsWith(dir) && name.charAt(dir.length()) == '/' && nameFilter.test(name)) {
        try (InputStream stream = entry.getInputStream(zipFile)) {
          consumer.accept(name, stream);
        }
      }
    }
  }

  @Override
  public @Nullable Attributes loadManifestAttributes() throws IOException {
    ImmutableZipEntry entry = zipFile.getEntry(JarFile.MANIFEST_NAME, MANIFEST_HASH_CODE);
    return entry != null ? new Manifest(new ByteArrayInputStream(entry.getData(zipFile))).getMainAttributes() : null;
  }

  @Override
  public @NotNull ClasspathCache.IndexRegistrar buildClassPathCacheData() throws IOException {
    // name hash is not added - doesn't make sense as fast lookup by name is supported by ImmutableZipFile
    ImmutableZipEntry packageIndex = zipFile.getEntry("__packageIndex__");
    if (packageIndex == null) {
      return computePackageIndex();
    }

    ByteBuffer buffer = packageIndex.getByteBuffer(zipFile);
    buffer.order(ByteOrder.LITTLE_ENDIAN);
    int[] classPackages = new int[buffer.getInt()];
    int[] resourcePackages = new int[buffer.getInt()];
    IntBuffer intBuffer = buffer.asIntBuffer();
    intBuffer.get(classPackages);
    intBuffer.get(resourcePackages);
    return (classMap, resourceMap, loader) -> {
      ClasspathCache.addResourceEntries(classPackages, classMap, loader);
      ClasspathCache.addResourceEntries(resourcePackages, resourceMap, loader);
    };
  }

  @NotNull
  private ClasspathCache.LoaderDataBuilder computePackageIndex() {
    ClasspathCache.LoaderDataBuilder builder = new ClasspathCache.LoaderDataBuilder(false);
    for (ImmutableZipEntry entry : zipFile.getRawNameSet()) {
      if (entry == null) {
        continue;
      }

      String name = entry.getName();
      if (name.endsWith(ClassPath.CLASS_EXTENSION)) {
        builder.addClassPackageFromName(name);
      }
      else {
        builder.addResourcePackageFromName(name);
      }
    }
    return builder;
  }

  @Override
  public @Nullable Class<?> findClass(String fileName, String className, JarLoader jarLoader, ClassPath.ClassDataConsumer classConsumer)
    throws IOException {
    ImmutableZipEntry entry = zipFile.getEntry(fileName);
    if (entry == null) {
      return null;
    }

    if (classConsumer.isByteBufferSupported(className, null)) {
      ByteBuffer buffer = entry.getByteBuffer(zipFile);
      try {
        return classConsumer.consumeClassData(className, buffer, jarLoader, null);
      }
      finally {
        entry.releaseBuffer(buffer);
      }
    }
    else {
      return classConsumer.consumeClassData(className, entry.getData(zipFile), jarLoader, null);
    }
  }

  @Override
  public @Nullable Resource getResource(@NotNull String name, @NotNull JarLoader jarLoader) throws IOException {
    ImmutableZipEntry entry = zipFile.getEntry(name);
    if (entry == null) {
      return null;
    }
    return new ZipFileResource(jarLoader, entry, zipFile);
  }

  private static final class ZipFileResource implements Resource {
    private final URL baseUrl;
    private URL url;
    private final ImmutableZipEntry entry;
    private final ImmutableZipFile file;
    private @Nullable("if mimicJarUrlConnection equals to false") final Path path;

    private ZipFileResource(@NotNull JarLoader jarLoader, @NotNull ImmutableZipEntry entry, @NotNull ImmutableZipFile file) {
      this.baseUrl = jarLoader.url;
      this.entry = entry;
      this.file = file;
      this.path = jarLoader.configuration.mimicJarUrlConnection ? jarLoader.path : null;
    }

    @Override
    public String toString() {
      return "ZipFileResource(name=" + entry.getName() + ", file=" + file + ')';
    }

    @Override
    public @NotNull URL getURL() {
      URL result = url;
      if (result == null) {
        URLStreamHandler handler = new MyJarUrlStreamHandler(entry, file, path);
        try {
          result = new URL(baseUrl, entry.getName(), handler);
        }
        catch (MalformedURLException e) {
          throw new RuntimeException(e);
        }
        url = result;
      }
      return result;
    }

    @Override
    public @NotNull InputStream getInputStream() throws IOException {
      return entry.getInputStream(file);
    }

    @Override
    public byte @NotNull [] getBytes() throws IOException {
      return entry.getData(file);
    }
  }

  private static final class MyJarUrlStreamHandler extends URLStreamHandler {
    private @NotNull final ImmutableZipEntry entry;
    private @NotNull final ImmutableZipFile file;
    private @Nullable final Path path;

    private MyJarUrlStreamHandler(@NotNull ImmutableZipEntry entry, @NotNull ImmutableZipFile file, @Nullable Path path) {
      this.entry = entry;
      this.file = file;
      this.path = path;
    }

    @Override
    protected URLConnection openConnection(URL url) throws MalformedURLException {
      return path == null ? new MyUrlConnection(url, entry, file) : new MyJarUrlConnection(url, entry, file, path);
    }
  }

  private static final class MyUrlConnection extends URLConnection {
    private final ImmutableZipEntry entry;
    private final ImmutableZipFile file;
    private byte[] data;

    MyUrlConnection(@NotNull URL url,
                    @NotNull ImmutableZipEntry entry,
                    @NotNull ImmutableZipFile file) {
      super(url);
      this.entry = entry;
      this.file = file;
    }

    private byte[] getData() throws IOException {
      byte[] result = data;
      if (result == null) {
        result = entry.getData(file);
        data = result;
      }
      return result;
    }

    @Override
    public void connect() throws IOException {
    }

    @Override
    public Object getContent() throws IOException {
      return getData();
    }

    @Override
    public InputStream getInputStream() throws IOException {
      return entry.getInputStream(file);
    }

    @Override
    public int getContentLength() {
      return entry.getUncompressedSize();
    }
  }

  private static final class MyJarUrlConnection extends JarURLConnection {
    private final ImmutableZipEntry entry;
    private final ImmutableZipFile file;
    private final Path path;
    private byte[] data;

    MyJarUrlConnection(@NotNull URL url,
                       @NotNull ImmutableZipEntry entry,
                       @NotNull ImmutableZipFile file,
                       @NotNull Path path) throws MalformedURLException {
      super(url);
      this.entry = entry;
      this.file = file;
      this.path = path;
    }

    private byte[] getData() throws IOException {
      byte[] result = data;
      if (result == null) {
        result = entry.getData(file);
        data = result;
      }
      return result;
    }

    @Override
    public void connect() throws IOException {
    }

    @Override
    public Object getContent() throws IOException {
      return getData();
    }

    @Override
    public InputStream getInputStream() throws IOException {
      return entry.getInputStream(file);
    }

    @Override
    public int getContentLength() {
      return entry.getUncompressedSize();
    }

    @Override
    public JarFile getJarFile() throws IOException {
      //noinspection LoggerInitializedWithForeignClass
      Logger.getInstance(ZipResourceFile.class).warn("Do not use URL connection as JarURLConnection");
      return new JarFile(path.toFile());
    }
  }
}
