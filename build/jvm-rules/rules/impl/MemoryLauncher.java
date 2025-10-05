// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

public final class MemoryLauncher {

  public static void main(String[] args) throws Throwable {
    if (args.length == 0) {
      System.err.println("Usage: java MemoryLauncher.java <jar> [args...]");
      System.exit(1);
    }

    Path jar = Paths.get(args[0]).toAbsolutePath();
    try (MemoryURLClassLoader classLoader = new MemoryURLClassLoader(jar)) {
      Thread.currentThread().setContextClassLoader(classLoader);

      String mainClassName = classLoader.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
      Class<?> mainClass = classLoader.loadClass(mainClassName);
      //noinspection ConfusingArgumentToVarargsMethod
      MethodHandles.lookup().findStatic(mainClass, "main", MethodType.methodType(void.class, String[].class))
        .invokeExact(Arrays.copyOfRange(args, 1, args.length));
    }
  }

  private static final class MemoryURLClassLoader extends URLClassLoader {
    private final URL myUrl;
    private final Manifest myManifest;
    private final Map<String, byte[]> myData;

    MemoryURLClassLoader(Path jar) throws IOException {
      super(new URL[]{jar.toUri().toURL()}, getPlatformClassLoader());
      myUrl = jar.toUri().toURL();

      myData = new HashMap<>();
      try (JarInputStream jis = new JarInputStream(new FileInputStream(jar.toFile()))) {
        myManifest = jis.getManifest();
        for (JarEntry e; (e = jis.getNextJarEntry()) != null; ) {
          if (!e.isDirectory()) {
            myData.put(e.getName(), jis.readAllBytes());
          }
        }
      }
    }

    Manifest getManifest() {
      return myManifest;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
      byte[] bytes = myData.get(name.replace('.', '/') + ".class");
      if (bytes == null) throw new ClassNotFoundException(name);
      return defineClass(name, bytes, 0, bytes.length);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
      byte[] bytes = myData.get(name);
      return bytes != null ? new ByteArrayInputStream(bytes) : null;
    }

    @Override
    public URL findResource(String name) {
      byte[] bytes = myData.get(name);
      if (bytes == null) return null;

      URLStreamHandler handler = new URLStreamHandler() {
        @Override
        protected URLConnection openConnection(URL url) {
          return new URLConnection(url) {
            @Override
            public void connect() { }

            @Override
            public InputStream getInputStream() {
              return new ByteArrayInputStream(bytes);
            }
          };
        }
      };
      try {
        return new URL(null, "jar:" + myUrl.toExternalForm() + "!/" + name, handler);
      }
      catch (MalformedURLException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public Enumeration<URL> findResources(String name) {
      URL url = findResource(name);
      return url != null ? Collections.enumeration(List.of(url)) : Collections.emptyEnumeration();
    }
  }
}
