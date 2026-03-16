package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;

/**
 * A ClassLoader that reads JAR files entirely into memory and does not keep any file handles open.
 * This is important for Windows compatibility where file locking can prevent JAR files from being
 * recompiled while a persistent Bazel worker is running.
 */
public class InMemoryClassLoader extends ClassLoader {
  private final Queue<Path> myUnprocessedPaths = new ArrayDeque<>();
  private final Map<String, byte[]> myClassData = new HashMap<>(); // class name (with slashes, e.g., "com/example/MyClass") to class bytes
  private final Map<String, byte[]> myResourceData = new HashMap<>(); // resource path to resource bytes

  public InMemoryClassLoader(List<Path> classpath, ClassLoader parent) {
    super(parent);
    myUnprocessedPaths.addAll(classpath);
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    byte[] bytes = loadData(name.replace('.', '/'), myClassData);
    if (bytes == null) {
      throw new ClassNotFoundException(name);
    }
    return defineClass(name, bytes, 0, bytes.length);
  }

  @Override
  protected URL findResource(String name) {
    String normalizedName = name.startsWith("/") ? name.substring(1) : name;

    // Check in class data first (for .class files)
    if (normalizedName.endsWith(".class")) {
      String className = normalizedName.substring(0, normalizedName.length() - ".class".length());
      byte[] data = loadData(className, myClassData);
      if (data != null) {
        return createInMemoryURL(normalizedName, data);
      }
    }

    // Check in resource data
    byte[] data = loadData(normalizedName, myResourceData);
    if (data == null) {
      return null;
    }
    return createInMemoryURL(normalizedName, data);
  }

  @Override
  protected Enumeration<URL> findResources(String name) {
    URL url = findResource(name);
    return url == null? Collections.emptyEnumeration() : Collections.enumeration(List.of(url));
  }

  @Override
  public InputStream getResourceAsStream(String name) {
    String normalizedName = name.startsWith("/") ? name.substring(1) : name;

    // Check in class data first (for .class files)
    if (normalizedName.endsWith(".class")) {
      String className = normalizedName.substring(0, normalizedName.length() - ".class".length());
      byte[] data = loadData(className, myClassData);
      if (data != null) {
        return new ByteArrayInputStream(data);
      }
    }

    // Check in resource data
    byte[] resData = loadData(normalizedName, myResourceData);
    if (resData != null) {
      return new ByteArrayInputStream(resData);
    }

    // Delegate to parent
    return super.getResourceAsStream(name);
  }

  private byte @Nullable [] loadData(String name, Map<String, byte[]> from) {
    try {
      byte[] bytes = from.get(name);
      while (bytes == null && !myUnprocessedPaths.isEmpty()) {
        loadFromJar(myUnprocessedPaths.remove(), myClassData, myResourceData);
        bytes = from.get(name);
      }
      return bytes;
    }
    catch (IOException e) {
      throw new RuntimeException("Failed to load bytes for: " + name, e);
    }
  }

  private static void loadFromJar(Path jarPath, Map<String, byte[]> classes, Map<String, byte[]> resources) throws IOException {
    try (JarInputStream jarStream = new JarInputStream(new BufferedInputStream(Files.newInputStream(jarPath)))) {
      ZipEntry entry = jarStream.getNextJarEntry();
      ByteArrayOutputStream buf = new ByteArrayOutputStream();
      while (entry != null) {
        if (!entry.isDirectory()) {
          String entryName = entry.getName();
          jarStream.transferTo(buf);
          byte[] content = buf.toByteArray();
          buf.reset();

          if (entryName.endsWith(".class")) {
            // Store class data with the path (without .class extension for lookup)
            String className = entryName.substring(0, entryName.length() - ".class".length());
            byte[] prev = classes.put(className, content);
            if (prev != null) {
              classes.put(className, prev); // first in classpath always wins
            }
          }
          else {
            // Store as resource
            byte[] prev = resources.put(entryName, content);
            if (prev != null) {
              resources.put(entryName, prev); // first in classpath always wins
            }
          }
        }
        entry = jarStream.getNextJarEntry();
      }
    }
  }

  private static URL createInMemoryURL(String name, byte[] data) {
    try {
      return new URL("memory", "", -1, "/" + name, new InMemoryURLStreamHandler(data));
    }
    catch (IOException e) {
      throw new RuntimeException("Failed to create in-memory URL for: " + name, e);
    }
  }

  private static class InMemoryURLStreamHandler extends URLStreamHandler {
    private final byte[] data;

    InMemoryURLStreamHandler(byte[] data) {
      this.data = data;
    }

    @Override
    protected URLConnection openConnection(URL url) {
      return new InMemoryURLConnection(url, data);
    }
  }

  private static class InMemoryURLConnection extends URLConnection {
    private final byte[] data;

    InMemoryURLConnection(URL url, byte[] data) {
      super(url);
      this.data = data;
    }

    @Override
    public void connect() {
      // No connection needed for in-memory data
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(data);
    }

    @Override
    public int getContentLength() {
      return data.length;
    }

    @Override
    public long getContentLengthLong() {
      return data.length;
    }
  }
}
