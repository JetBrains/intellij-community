/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs.impl.jrt;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.impl.ArchiveHandler;
import com.intellij.reference.SoftReference;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.*;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

class JrtHandler extends ArchiveHandler {
  private static final URI ROOT_URI = URI.create("jrt:/");
  private static final Map<String, Object> EMPTY_ENV = Collections.emptyMap();

  private static class JrtEntryInfo extends EntryInfo {
    private final String myModule;

    public JrtEntryInfo(EntryInfo parent, @NotNull String shortName, @NotNull String module, long length, long timestamp) {
      super(parent, shortName, false, length, timestamp);
      myModule = module;
    }
  }

  private SoftReference<Object> myFileSystem;

  public JrtHandler(@NotNull String path) {
    super(path);
  }

  private synchronized Object getFileSystem() throws IOException {
    Object fs = SoftReference.dereference(myFileSystem);
    if (fs == null) {
      URL url = new File(getFile(), "jrt-fs.jar").toURI().toURL();
      ClassLoader loader = new URLClassLoader(new URL[]{url}, null);
      //fs = FileSystems.newFileSystem(ROOT_URI, EMPTY_ENV, loader);
      fs = call(newFileSystem, ROOT_URI, EMPTY_ENV, loader);
      myFileSystem = new SoftReference<Object>(fs);
    }
    return fs;
  }

  @NotNull
  @Override
  protected Map<String, EntryInfo> createEntriesMap() throws IOException {
    final Map<String, EntryInfo> map = ContainerUtil.newHashMap();
    map.put("", createRootEntry());

    //FileSystem fs = (FileSystem)getFileSystem();
    //Path root = fs.getPath("/modules"); // b74+
    //if (!Files.exists(root)) root = fs.getPath("/");
    Object fs = getFileSystem();
    Object root = call(getPath, fs, "/modules", ArrayUtil.EMPTY_STRING_ARRAY);
    if (Boolean.FALSE.equals(call(exists, root, linkOptions))) {
      root = call(getPath, fs, "/", ArrayUtil.EMPTY_STRING_ARRAY);
    }

    final int start = root.toString().length() + 1;
    //Files.walk(root).forEachOrdered((p) -> {
    Object stream = call(walk, root, Array.newInstance(cls("java.nio.file.FileVisitOption"), 0));
    call(forEachOrdered, stream, Proxy.newProxyInstance(
      getClass().getClassLoader(), new Class[]{cls("java.util.function.Consumer")}, new InvocationHandler() {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
          //String path = p.toString();
          String path = args[0].toString();

          int p = path.indexOf('/', start);
          if (p < 0) return null;
          String module = path.substring(1, p);
          path = path.substring(p + 1);
          if (map.containsKey(path)) return null;

          String shortName;
          EntryInfo parent;

          p = path.lastIndexOf('/');
          if (p < 0) {
            shortName = path;
            parent = map.get("");
          }
          else {
            shortName = path.substring(p + 1);
            parent = map.get(path.substring(0, p));
          }
          assert parent != null : path;

          //BasicFileAttributes attributes = Files.readAttributes(p, BasicFileAttributes.class);
          //boolean dir = attributes.isDirectory();
          //long length = attributes.size();
          //long modified = attributes.lastModifiedTime().toMillis();
          Object attributes = call(readAttributes, args[0], cls("java.nio.file.attribute.BasicFileAttributes"), linkOptions);
          boolean dir = (Boolean)call(isDirectory, attributes);
          long length = (Long)call(size, attributes);
          long modified = (Long)call(toMillis, call(lastModifiedTime, attributes));
          EntryInfo entry = dir ? new EntryInfo(parent, shortName, true, length, modified)
                                : new JrtEntryInfo(parent, shortName, module, length, modified);
          map.put(path, entry);

          return null;
        }
      }));
    //});

    return map;
  }

  @NotNull
  @Override
  public byte[] contentsToByteArray(@NotNull String relativePath) throws IOException {
    EntryInfo entry = getEntryInfo(relativePath);
    if (!(entry instanceof JrtEntryInfo)) {
      return ArrayUtil.EMPTY_BYTE_ARRAY;
    }

    //FileSystem fs = (FileSystem)getFileSystem();
    //Path path = fs.getPath(((JrtEntryInfo)entry).myModule, relativePath);
    //return Files.readAllBytes(path);
    Object fs = getFileSystem();
    Object path = call(getPath, fs, ((JrtEntryInfo)entry).myModule, new String[]{relativePath});
    return (byte[])call(readAllBytes, path);
  }

  // reflection stuff
  // todo[r.sh] drop reflection after migration to Java 8

  private static final Method newFileSystem = method("java.nio.file.FileSystems#newFileSystem", URI.class, Map.class, ClassLoader.class);
  private static final Method getPath = method("java.nio.file.FileSystem#getPath", String.class, String[].class);
  private static final Method exists = method("java.nio.file.Files#exists", cls("java.nio.file.Path"), cls("java.nio.file.LinkOption", true));
  private static final Method walk = method("java.nio.file.Files#walk", cls("java.nio.file.Path"), cls("java.nio.file.FileVisitOption", true));
  private static final Method forEachOrdered = method("java.util.stream.Stream#forEachOrdered", cls("java.util.function.Consumer"));
  private static final Method readAttributes =
    method("java.nio.file.Files#readAttributes", cls("java.nio.file.Path"), Class.class, cls("java.nio.file.LinkOption", true));
  private static final Method isDirectory = method("java.nio.file.attribute.BasicFileAttributes#isDirectory");
  private static final Method size = method("java.nio.file.attribute.BasicFileAttributes#size");
  private static final Method lastModifiedTime = method("java.nio.file.attribute.BasicFileAttributes#lastModifiedTime");
  private static final Method toMillis = method("java.nio.file.attribute.FileTime#toMillis");
  private static final Method readAllBytes = method("java.nio.file.Files#readAllBytes", cls("java.nio.file.Path"));
  private static final Object linkOptions = Array.newInstance(cls("java.nio.file.LinkOption"), 0);

  private static Class<?> cls(String name) {
    return cls(name, false);
  }

  private static Class<?> cls(String name, boolean array) {
    try {
      if (array) name = "[L" + name + ";";
      return Class.forName(name);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static Method method(String name, Class<?>... parameterTypes) {
    try {
      List<String> parts = StringUtil.split(name, "#");
      Class<?> aClass = cls(parts.get(0));
      Method method = aClass.getMethod(parts.get(1), parameterTypes);
      method.setAccessible(true);
      return method;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static Object call(Method method, Object... args) {
    try {
      if (Modifier.isStatic(method.getModifiers())) {
        return method.invoke(null, args);
      }
      else {
        return method.invoke(args[0], Arrays.copyOfRange(args, 1, args.length));
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
