/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ConcurrentFactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.ClassWriter;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.Type;

import java.lang.reflect.Modifier;

/**
 * @author peter
 */
public class InstanceofCheckerGenerator {
  private static final InstanceofCheckerGenerator ourInstance;

  static {
    try {
      ourInstance = new InstanceofCheckerGenerator();
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  public static InstanceofCheckerGenerator getInstance() {
    return ourInstance;
  }

  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private final ConcurrentFactoryMap<Class, Condition<Object>> myCache = new ConcurrentFactoryMap<Class, Condition<Object>>() {
    @Override
    protected Condition<Object> create(final Class key) {
      if (key.isAnonymousClass() || Modifier.isPrivate(key.getModifiers())) {
        return new Condition<Object>() {
          @Override
          public boolean value(Object o) {
            return key.isInstance(o);
          }
        };
      }

      String name = "com.intellij.util.InstanceofChecker$$$$$" + key.getName().replace('.', '$');
      //noinspection unchecked
      return (Condition<Object>)ReflectionUtil.newInstance(obtainClass(key, name, generateConditionClass(key, name)));
    }
  };

  private synchronized Class obtainClass(Class checkedClass, String name, byte[] bytes) {
    ClassLoader loader = checkedClass.getClassLoader();
    if (loader == null) loader = InstanceofCheckerGenerator.class.getClassLoader();
    try {
      return loader.loadClass(name);
    }
    catch (ClassNotFoundException ignore) {
    }

    try {
      return ReflectionUtil.defineClass(name, bytes, loader);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public Condition<Object> getInstanceofChecker(final Class<?> someClass) {
    return myCache.get(someClass);
  }

  private static String toInternalName(Class<?> someClass) {
    return toInternalName(someClass.getName());
  }

  @NotNull
  private static String toInternalName(String name) {
    return name.replace('.', '/');
  }

  private static byte[] generateConditionClass(Class<?> checkedClass, final String generatedName) {
    ClassWriter cv = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cv.visit(Opcodes.V1_2, Modifier.PUBLIC, toInternalName(generatedName), null, toInternalName(Object.class), new String[]{toInternalName(Condition.class)});

    defaultConstructor(cv);

    conditionValue(checkedClass, cv);

    cv.visitEnd();
    return cv.toByteArray();
  }

  private static void defaultConstructor(ClassWriter cv) {
    MethodVisitor mv = cv.visitMethod(Modifier.PUBLIC, "<init>", "()V", null, null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, toInternalName(Object.class), "<init>", "()V", false);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

  private static void conditionValue(Class<?> checkedClass, ClassWriter cv) {
    MethodVisitor mv = cv.visitMethod(Modifier.PUBLIC, "value", "(L" + toInternalName(Object.class) + ";)Z", null, null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.ALOAD, 1);
    mv.visitTypeInsn(Opcodes.INSTANCEOF, Type.getType(checkedClass).getInternalName());
    mv.visitInsn(Opcodes.IRETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
  }

}
