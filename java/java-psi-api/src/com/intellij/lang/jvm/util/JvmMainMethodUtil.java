// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.util;

import com.intellij.lang.jvm.*;
import com.intellij.lang.jvm.types.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.lang.jvm.util.JvmUtil.resolveClass;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;
import static com.intellij.util.containers.ContainerUtil.find;
import static java.util.Objects.requireNonNull;

// TODO support com.intellij.codeInsight.runner.JavaMainMethodProvider
public class JvmMainMethodUtil {

  private static final String MAIN = "main";

  private JvmMainMethodUtil() {}

  public static boolean isMainMethod(@NotNull JvmMethod method) {
    if (!MAIN.equals(method.getName())) return false;
    final JvmClass containingClass = method.getContainingClass();
    return containingClass != null && canBeMainClass(containingClass) && hasMainMethodSignature(method);
  }

  public static boolean hasMainMethodInHierarchy(@NotNull JvmClass clazz) {
    if (!canBeMainClass(clazz)) return false;
    return findMainMethodInHierarchy(clazz) != null;
  }

  private static boolean canBeMainClass(@NotNull JvmClass clazz) {
    if (clazz.getName() == null) return false; // anonymous classes
    final JvmClassKind kind = clazz.getClassKind();
    if (kind == JvmClassKind.ANNOTATION) return false;
    return clazz.getContainingClass() == null || clazz.hasModifier(JvmModifier.STATIC);
  }

  @Nullable
  private static JvmMethod findMainMethodInHierarchy(@NotNull JvmClass clazz) {
    return JvmHierarchyUtil.traverseSupers(clazz, JvmMainMethodUtil::findMainMethodInClass);
  }

  @Nullable
  private static JvmMethod findMainMethodInClass(@NotNull JvmClass clazz) {
    JvmMethod[] candidates = clazz.findMethodsByName(MAIN);
    return find(candidates, JvmMainMethodUtil::hasMainMethodSignature);
  }

  /**
   * @return {@code true} if the method matches {@code public static void xxx(String[] args) {}}, otherwise {@code false}
   */
  private static boolean hasMainMethodSignature(@NotNull JvmMethod method) {
    if (method.isConstructor()) return false;
    if (!method.hasModifier(JvmModifier.PUBLIC)) return false;
    if (!method.hasModifier(JvmModifier.STATIC)) return false;

    final JvmType returnType = requireNonNull(method.getReturnType(), () -> "Non-constructors should have return type: " + method);
    if (!isVoid(returnType)) return false;

    final JvmParameter[] parameters = method.getParameters();
    if (parameters.length != 1) return false;

    return isStringArray(parameters[0].getType());
  }

  @Contract(value = "null -> false", pure = true)
  private static boolean isVoid(@NotNull JvmType type) {
    return type instanceof JvmPrimitiveType && ((JvmPrimitiveType)type).getKind() == JvmPrimitiveTypeKind.VOID;
  }

  private static boolean isStringArray(@NotNull JvmType type) {
    if (!(type instanceof JvmArrayType)) return false;
    final JvmType componentType = ((JvmArrayType)type).getComponentType();
    if (!(componentType instanceof JvmReferenceType)) return false;
    final JvmClass resolved = resolveClass((JvmReferenceType)componentType);
    return resolved != null && JAVA_LANG_STRING.equals(resolved.getQualifiedName());
  }
}
