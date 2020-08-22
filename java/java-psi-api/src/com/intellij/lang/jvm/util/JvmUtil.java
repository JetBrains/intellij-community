// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.util;

import com.intellij.lang.jvm.JvmClass;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.JvmTypeDeclaration;
import com.intellij.lang.jvm.types.JvmReferenceType;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.containers.ContainerUtil.mapNotNull;

public final class JvmUtil {
  private static final JvmModifier[] ACCESS_MODIFIERS = {
    JvmModifier.PRIVATE, JvmModifier.PACKAGE_LOCAL, JvmModifier.PROTECTED, JvmModifier.PUBLIC
  };

  private JvmUtil() {}

  @NotNull
  static Iterable<JvmClass> resolveClasses(JvmReferenceType @NotNull [] types) {
    return mapNotNull(types, JvmUtil::resolveClass);
  }

  @Contract("null -> null")
  @Nullable
  public static JvmClass resolveClass(@Nullable JvmReferenceType type) {
    if (type == null) return null;
    JvmTypeDeclaration resolved = type.resolve();
    return resolved instanceof JvmClass ? (JvmClass)resolved : null;
  }

  /**
   * JVM language version of {@link PsiUtil#getAccessModifier(int)}
   */
  @PsiModifier.ModifierConstant
  @NotNull
  public static JvmModifier getAccessModifier(@PsiUtil.AccessLevel int accessLevel) {
    assert accessLevel > 0 && accessLevel <= ACCESS_MODIFIERS.length : accessLevel;
    return ACCESS_MODIFIERS[accessLevel - 1];
  }
}
