// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.util.io.Murmur3_32Hash;
import com.intellij.util.zip.ImmutableZipEntry;
import com.intellij.util.zip.ImmutableZipFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
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
  private static final int MANIFEST_HASH_CODE = Murmur3_32Hash.MURMUR3_32.hashString(JarFile.MANIFEST_NAME, 0, JarFile.MANIFEST_NAME.length());

  private final ImmutableZipFile zipFile;

  public ZipResourceFile(@NotNull Path file) {
    try {
      zipFile = ImmutableZipFile.load(file, buffer -> {
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.getInt();
      });
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
    if (entry != null) {
      return new Manifest(new ByteArrayInputStream(entry.getData(zipFile))).getMainAttributes();
    }
    return null;
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
    return new ZipFileResource(jarLoader.url, entry, zipFile);
  }

  private static final class ZipFileResource implements Resource {
    private final URL baseUrl;
    private URL url;
    private final ImmutableZipEntry entry;
    private final ImmutableZipFile file;

    private ZipFileResource(@NotNull URL baseUrl, @NotNull ImmutableZipEntry entry, @NotNull ImmutableZipFile file) {
      this.baseUrl = baseUrl;
      this.entry = entry;
      this.file = file;
    }

    @Override
    public String toString() {
      return "ZipFileResource(name=" + entry.getName() + ", file=" + file + ')';
    }

    @Override
    public @NotNull URL getURL() {
      URL result = url;
      if (result == null) {
        try {
          result = new URL(baseUrl, entry.getName(), new MyURLStreamHandler(entry, file));
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

  private static final class MyURLStreamHandler extends URLStreamHandler {
    private @NotNull final ImmutableZipEntry entry;
    private @NotNull final ImmutableZipFile file;

    private MyURLStreamHandler(@NotNull ImmutableZipEntry entry, @NotNull ImmutableZipFile file) {
      this.entry = entry;
      this.file = file;
    }

    @Override
    protected URLConnection openConnection(URL url) {
      return new MyURLConnection(url, entry, file);
    }
  }

  private static final class MyURLConnection extends URLConnection {
    private final ImmutableZipEntry entry;
    private final ImmutableZipFile file;
    private byte[] data;

    MyURLConnection(@NotNull URL url, @NotNull ImmutableZipEntry entry, @NotNull ImmutableZipFile file) {
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
}
