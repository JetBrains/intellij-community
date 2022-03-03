// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.lang.ZipFile.ZipResource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

@SuppressWarnings("SuspiciousPackagePrivateAccess")
final class ZipResourceFile implements ResourceFile {
  private final ZipFile zipFile;

  ZipResourceFile(@NotNull Path file) {
    ZipFilePool pool = ZipFilePool.POOL;
    try {
      if (pool == null) {
        zipFile = ImmutableZipFile.load(file);
      }
      else {
        Object zipFile = pool.loadZipFile(file);
        this.zipFile = (ZipFile)zipFile;
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
    zipFile.processResources(dir, nameFilter, consumer);
  }

  @Override
  public @Nullable Attributes loadManifestAttributes() throws IOException {
    InputStream stream = zipFile.getInputStream(JarFile.MANIFEST_NAME);
    if (stream == null) {
      return null;
    }

    try {
      return new Manifest(stream).getMainAttributes();
    }
    finally {
      stream.close();
    }
  }

  @Override
  public @NotNull ClasspathCache.IndexRegistrar buildClassPathCacheData() {
    // name hash is not added - doesn't make sense as fast lookup by name is supported by ImmutableZipFile
    if (zipFile instanceof ImmutableZipFile) {
      ImmutableZipFile file = (ImmutableZipFile)zipFile;
      return new ClasspathCache.IndexRegistrar() {
        @Override
        public int classPackageCount() {
          return file.classPackages.length;
        }

        @Override
        public int resourcePackageCount() {
          return file.resourcePackages.length;
        }

        @Override
        public long[] classPackages() {
          return file.classPackages;
        }

        @Override
        public long[] resourcePackages() {
          return file.resourcePackages;
        }
      };
    }
    else {
      return computePackageIndex();
    }
  }

  private @NotNull ClasspathCache.LoaderDataBuilder computePackageIndex() {
    ClasspathCache.LoaderDataBuilder builder = new ClasspathCache.LoaderDataBuilder();
    for (ImmutableZipEntry entry : ((HashMapZipFile)zipFile).getRawNameSet()) {
      if (entry != null) {
        builder.addPackageFromName(entry.name);
      }
    }
    return builder;
  }

  @Override
  public @Nullable Class<?> findClass(String fileName, String className, JarLoader jarLoader, ClassPath.ClassDataConsumer classConsumer)
    throws IOException {
    if (classConsumer.isByteBufferSupported(className)) {
      ByteBuffer buffer = zipFile.getByteBuffer(fileName);
      if (buffer == null) {
        return null;
      }

      try {
        return classConsumer.consumeClassData(className, buffer, jarLoader);
      }
      finally {
        zipFile.releaseBuffer(buffer);
      }
    }
    else {
      byte[] data = zipFile.getData(fileName);
      if (data == null) {
        return null;
      }
      return classConsumer.consumeClassData(className, data, jarLoader);
    }
  }

  @Override
  public @Nullable Resource getResource(@NotNull String name, @NotNull JarLoader jarLoader) {
    ZipResource entry = zipFile.getResource(name);
    return entry == null ? null : new ZipFileResource(jarLoader, entry, name);
  }

  private static final class ZipFileResource implements Resource {
    private final URL baseUrl;
    private URL url;
    private final String name;
    private final ZipResource entry;
    private @Nullable("if mimicJarUrlConnection equals to false") final Path path;

    private ZipFileResource(@NotNull JarLoader jarLoader, @NotNull ZipResource entry, @NotNull String name) {
      this.baseUrl = jarLoader.url;
      this.entry = entry;
      this.name = name;
      this.path = jarLoader.configuration.mimicJarUrlConnection ? jarLoader.getPath() : null;
    }

    @Override
    public String toString() {
      return "ZipFileResource(name=" + entry + ")";
    }

    @Override
    public @NotNull URL getURL() {
      URL result = url;
      if (result == null) {
        URLStreamHandler handler = new MyJarUrlStreamHandler(entry, path);
        try {
          result = new URL(baseUrl, name, handler);
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
      return entry.getInputStream();
    }

    @Override
    public byte @NotNull [] getBytes() throws IOException {
      return entry.getData();
    }
  }

  private static final class MyJarUrlStreamHandler extends URLStreamHandler {
    private @NotNull final ZipResource entry;
    private @Nullable final Path path;

    private MyJarUrlStreamHandler(@NotNull ZipResource entry, @Nullable Path path) {
      this.entry = entry;
      this.path = path;
    }

    @Override
    protected URLConnection openConnection(URL url) throws MalformedURLException {
      return path == null ? new MyUrlConnection(url, entry) : new MyJarUrlConnection(url, entry, path);
    }
  }

  private static final class MyUrlConnection extends URLConnection {
    private final ZipResource entry;
    private byte[] data;

    MyUrlConnection(@NotNull URL url, @NotNull ZipResource entry) {
      super(url);
      this.entry = entry;
    }

    private byte[] getData() throws IOException {
      byte[] result = data;
      if (result == null) {
        result = entry.getData();
        data = result;
      }
      return result;
    }

    @Override
    public void connect() {
    }

    @Override
    public Object getContent() throws IOException {
      return getData();
    }

    @Override
    public InputStream getInputStream() throws IOException {
      return entry.getInputStream();
    }

    @Override
    public int getContentLength() {
      return entry.getUncompressedSize();
    }
  }

  private static final class MyJarUrlConnection extends JarURLConnection {
    private final ZipResource entry;
    private final Path path;
    private byte[] data;

    MyJarUrlConnection(@NotNull URL url, @NotNull ZipResource entry, @NotNull Path path) throws MalformedURLException {
      super(url);
      this.entry = entry;
      this.path = path;
    }

    private byte[] getData() throws IOException {
      byte[] result = data;
      if (result == null) {
        result = entry.getData();
        data = result;
      }
      return result;
    }

    @Override
    public void connect() {
    }

    @Override
    public Object getContent() throws IOException {
      return getData();
    }

    @Override
    public InputStream getInputStream() throws IOException {
      return entry.getInputStream();
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
