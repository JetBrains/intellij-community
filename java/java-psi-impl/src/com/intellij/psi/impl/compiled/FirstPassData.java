// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.EntryStream;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.*;

import java.io.IOException;
import java.util.*;

import static com.intellij.util.BitUtil.isSet;

/**
 * Information retrieved during the first pass of a class file parsing
 */
class FirstPassData implements Function<@NotNull String, @NotNull String> {
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

  private static final FirstPassData EMPTY = new FirstPassData(null, null);
  private final @Nullable Map<String, InnerClassEntry> myMap;
  private final @NotNull Set<String> myNonStatic;
  private final @Nullable String myVarArgRecordComponent;

  private FirstPassData(@Nullable Map<String, InnerClassEntry> map, @Nullable String component) {
    myMap = map;
    myVarArgRecordComponent = component;
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
   * @param componentName record component name
   * @return true if given component is var-arg
   */
  public boolean isVarArgComponent(@NotNull String componentName) {
    return componentName.equals(myVarArgRecordComponent);
  }

  /**
   * @param jvmNames array JVM type names (e.g. throws list, implements list)
   * @return list of TypeInfo objects that correspond to given types
   */
  @Contract("null -> null; !null -> !null")
  public List<TypeInfo> createTypes(String @Nullable [] jvmNames) {
    return jvmNames == null ? null :
           ContainerUtil.map(jvmNames, jvmName -> new TypeInfo(mapJvmClassNameToJava(jvmName)));
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

  static @NotNull FirstPassData create(Object classSource) {
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

  private static @NotNull FirstPassData fromClassBytes(byte[] classBytes) {
    
    class FirstPassVisitor extends ClassVisitor {
      final Map<String, InnerClassEntry> mapping = new HashMap<>();
      Set<String> varArgConstructors;
      StringBuilder canonicalSignature;
      String lastComponent;

      FirstPassVisitor() {
        super(Opcodes.API_VERSION);
      }

      @Override
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        if (isSet(access, Opcodes.ACC_RECORD)) {
          varArgConstructors = new HashSet<>();
          canonicalSignature = new StringBuilder("(");
        }
      }

      @Override
      public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
        if (canonicalSignature != null) {
          canonicalSignature.append(descriptor);
          lastComponent = name;
        }
        return null;
      }

      @Override
      public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (varArgConstructors != null && name.equals("<init>") && isSet(access, Opcodes.ACC_VARARGS)) {
          varArgConstructors.add(descriptor);
        }
        return null;
      }

      @Override
      public void visitInnerClass(String name, String outerName, String innerName, int access) {
        if (outerName != null && innerName != null) {
          mapping.put(name, new InnerClassEntry(outerName, innerName, isSet(access, Opcodes.ACC_STATIC)));
        }
      }
    }

    FirstPassVisitor visitor = new FirstPassVisitor();
    try {
      new ClassReader(classBytes).accept(visitor, ClsFileImpl.EMPTY_ATTRIBUTES, 
                                         ClassReader.SKIP_DEBUG | ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
    }
    catch (Exception ignored) {
    }
    String varArgComponent = null;
    if (visitor.canonicalSignature != null) {
      visitor.canonicalSignature.append(")V");
      if (visitor.varArgConstructors.contains(visitor.canonicalSignature.toString())) {
        varArgComponent = visitor.lastComponent;
      }
    }
    if (varArgComponent == null && visitor.mapping.isEmpty()) {
      return EMPTY;
    }
    return new FirstPassData(visitor.mapping, varArgComponent);
  }
}
