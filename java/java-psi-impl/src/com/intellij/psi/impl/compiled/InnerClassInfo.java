// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.EntryStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.util.*;

import static com.intellij.util.BitUtil.isSet;

/**
 * Information about inner classes stored in a class file
 */
class InnerClassInfo implements Function<@NotNull String, @NotNull String> {
  private static class InnerClassEntry {
    final @NotNull String myOuterName;
    final @Nullable String myInnerName;
    final boolean myStatic;

    private InnerClassEntry(@NotNull String outerName, @Nullable String innerName, boolean aStatic) {
      myOuterName = outerName;
      myInnerName = innerName;
      myStatic = aStatic;
    }
  }

  private static final InnerClassInfo EMPTY = new InnerClassInfo(null);
  private final @Nullable Map<String, InnerClassEntry> myMap;
  private final @NotNull Set<String> myNonStatic;

  private InnerClassInfo(@Nullable Map<String, InnerClassEntry> map) {
    myMap = map;
    if (map != null) {
      List<String> jvmNames = EntryStream.of(map).filterValues(e -> !e.myStatic).keys().toList();
      myNonStatic = ContainerUtil.map2Set(jvmNames, this::mapJvmClassNameToJava);
    }
    else {
      myNonStatic = Collections.emptySet();
    }
  }

  @Override
  public @NotNull String fun(@NotNull String jvmName) {
    return mapJvmClassNameToJava(jvmName);
  }

  /**
   * @param javaName java class name
   * @return nesting level: number of enclosing classes for which this class is non-static
   */
  public int getInnerDepth(@NotNull String javaName) {
    int depth = 0;
    while (!javaName.isEmpty() && myNonStatic.contains(javaName)) {
      depth++;
      javaName = StringUtil.getPackageName(javaName);
    }
    return depth;
  }

  /**
   * @param jvmName JVM class name like java/util/Map$Entry
   * @return Java class name like java.util.Map.Entry
   */
  public @NotNull String mapJvmClassNameToJava(@NotNull String jvmName) {
    if (myMap == null) {
      return StubBuildingVisitor.GUESSING_MAPPER.fun(jvmName);
    }
    String className = jvmName;

    if (className.indexOf('$') >= 0) {
      InnerClassEntry p = myMap.get(className);
      if (p == null) {
        return StubBuildingVisitor.GUESSING_MAPPER.fun(className);
      }
      className = p.myOuterName;
      if (p.myInnerName != null) {
        className = mapJvmClassNameToJava(p.myOuterName) + '.' + p.myInnerName;
        myMap.put(className, new InnerClassEntry(className, null, true));
      }
    }

    return className.replace('/', '.');
  }

  static @NotNull InnerClassInfo create(Object classSource) {
    byte[] bytes = null;
    if (classSource instanceof ClsFileImpl.FileContentPair) {
      bytes = ((ClsFileImpl.FileContentPair)classSource).getContent();
    }
    else if (classSource instanceof VirtualFile) {
      try {
        bytes = ((VirtualFile)classSource).contentsToByteArray(false);
      }
      catch (IOException ignored) {
      }
    }

    if (bytes != null) {
      return fromClassBytes(bytes);
    }

    return EMPTY;
  }

  private static @NotNull InnerClassInfo fromClassBytes(byte[] classBytes) {
    final Map<String, InnerClassEntry> mapping = new HashMap<>();

    try {
      new ClassReader(classBytes).accept(new ClassVisitor(StubBuildingVisitor.ASM_API) {
        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
          if (outerName != null && innerName != null) {
            mapping.put(name, new InnerClassEntry(outerName, innerName, isSet(access, Opcodes.ACC_STATIC)));
          }
        }
      }, ClsFileImpl.EMPTY_ATTRIBUTES, ClassReader.SKIP_DEBUG | ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
    }
    catch (Exception ignored) {
    }
    if (mapping.isEmpty()) {
      return EMPTY;
    }
    return new InnerClassInfo(mapping);
  }
}
