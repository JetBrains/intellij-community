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
import java.nio.file.NoSuchFileException;
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
    try (InputStream stream = zipFile.getInputStream(JarFile.MANIFEST_NAME)) {
      if (stream == null) {
        return null;
      }
      return new Manifest(stream).getMainAttributes();
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
    private final JarLoader jarLoader;
    private URL url;
    private final String name;
    private final ZipResource entry;

    private ZipFileResource(@NotNull JarLoader jarLoader, @NotNull ZipResource entry, @NotNull String name) {
      this.jarLoader = jarLoader;
      this.entry = entry;
      this.name = name;
    }

    @Override
    public String toString() {
      return "ZipFileResource(name=" + entry + ")";
    }

    @Override
    public @NotNull URL getURL() {
      URL result = url;
      if (result == null) {
        URLStreamHandler handler = new MyJarUrlStreamHandler(entry, jarLoader);
        try {
          result = new URL(jarLoader.url, name, handler);
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
    private @NotNull final JarLoader jarLoader;

    private MyJarUrlStreamHandler(@NotNull ZipResource entry, @NotNull JarLoader jarLoader) {
      this.entry = entry;
      this.jarLoader = jarLoader;
    }

    @Override
    protected URLConnection openConnection(URL url) throws MalformedURLException {
      return jarLoader.configuration.mimicJarUrlConnection ? new MyJarUrlConnection(url, entry, jarLoader) : new MyUrlConnection(url, entry);
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
    private ZipResource effectiveEntry;
    private final JarLoader jarLoader;
    private byte[] data;

    MyJarUrlConnection(@NotNull URL url, @NotNull ZipResource entry, @NotNull JarLoader jarLoader) throws MalformedURLException {
      super(url);
      String entryName = getEntryName();
      effectiveEntry = entryName == null || entryName.equals(entry.getPath()) ? entry : null;
      this.jarLoader = jarLoader;
    }

    private byte[] getData() throws IOException {
      byte[] result = data;
      if (result == null) {
        connect();
        result = effectiveEntry.getData();
        data = result;
      }
      return result;
    }

    @Override
    public void connect() throws IOException {
      if (effectiveEntry == null) {
        Resource resource = jarLoader.zipFile.getResource(getEntryName(), jarLoader);
        if (resource == null) {
          throw new NoSuchFileException("Cannot find `" + getEntryName() + "` in " + jarLoader.getPath());
        }
        effectiveEntry = ((ZipFileResource)resource).entry;
      }
    }

    @Override
    public Object getContent() throws IOException {
      return getData();
    }

    @Override
    public InputStream getInputStream() throws IOException {
      connect();
      return effectiveEntry.getInputStream();
    }

    @Override
    public long getContentLengthLong() {
      return getContentLength();
    }

    @Override
    public int getContentLength() {
      try {
        connect();
      }
      catch (IOException e) {
        return -1;
      }
      return effectiveEntry.getUncompressedSize();
    }

    @Override
    public JarFile getJarFile() throws IOException {
      //noinspection LoggerInitializedWithForeignClass
      Logger.getInstance(ZipResourceFile.class).warn("Do not use URL connection as JarURLConnection");
      return new JarFile(jarLoader.getPath().toFile());
    }
  }
}
