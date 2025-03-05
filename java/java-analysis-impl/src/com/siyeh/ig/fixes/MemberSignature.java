// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.util.ClassUtil;
import org.jetbrains.annotations.NonNls;

import java.lang.reflect.Modifier;

public class MemberSignature implements Comparable<MemberSignature> {
  private static final @NonNls String CONSTRUCTOR_NAME = "<init>";
  private static final @NonNls String INITIALIZER_SIGNATURE = "()V";
  private static final @NonNls MemberSignature ASSERTIONS_DISABLED_FIELD =
    new MemberSignature("$assertionsDisabled", Modifier.STATIC | Modifier.FINAL, "Z");
  private static final @NonNls MemberSignature PACKAGE_PRIVATE_CONSTRUCTOR =
    new MemberSignature(CONSTRUCTOR_NAME, 0, INITIALIZER_SIGNATURE);
  private static final @NonNls MemberSignature PUBLIC_CONSTRUCTOR =
    new MemberSignature(CONSTRUCTOR_NAME, Modifier.PUBLIC, INITIALIZER_SIGNATURE);
  private static final @NonNls MemberSignature STATIC_INITIALIZER =
    new MemberSignature("<clinit>", Modifier.STATIC, INITIALIZER_SIGNATURE);

  private final int modifiers;
  private final String name;
  private final String signature;

  public MemberSignature(PsiField field) {
    modifiers = calculateModifierBitmap(field.getModifierList());
    name = field.getName();
    signature = ClassUtil.getBinaryPresentation(field.getType());
  }

  public MemberSignature(PsiMethod method) {
    modifiers = calculateModifierBitmap(method.getModifierList());
    signature = ClassUtil.getAsmMethodSignature(method).replace('/', '.');
    name = method.isConstructor() ? CONSTRUCTOR_NAME : method.getName();
  }

  public MemberSignature(@NonNls String name, int modifiers, @NonNls String signature) {
    this.name = name;
    this.modifiers = modifiers;
    this.signature = signature;
  }

  public static int calculateModifierBitmap(PsiModifierList modifierList) {
    int modifiers = 0;
    if (modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
      modifiers |= Modifier.PUBLIC;
    }
    if (modifierList.hasModifierProperty(PsiModifier.PRIVATE)) {
      modifiers |= Modifier.PRIVATE;
    }
    if (modifierList.hasModifierProperty(PsiModifier.PROTECTED)) {
      modifiers |= Modifier.PROTECTED;
    }
    if (modifierList.hasModifierProperty(PsiModifier.STATIC)) {
      modifiers |= Modifier.STATIC;
    }
    if (modifierList.hasModifierProperty(PsiModifier.FINAL)) {
      modifiers |= Modifier.FINAL;
    }
    if (modifierList.hasModifierProperty(PsiModifier.VOLATILE)) {
      modifiers |= Modifier.VOLATILE;
    }
    if (modifierList.hasModifierProperty(PsiModifier.TRANSIENT)) {
      modifiers |= Modifier.TRANSIENT;
    }
    if (modifierList.hasModifierProperty(PsiModifier.ABSTRACT)) {
      modifiers |= Modifier.ABSTRACT;
    }
    if (modifierList.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
      modifiers |= Modifier.SYNCHRONIZED;
    }
    if (modifierList.hasModifierProperty(PsiModifier.NATIVE)) {
      modifiers |= Modifier.NATIVE;
    }
    if (modifierList.hasModifierProperty(PsiModifier.STRICTFP)) {
      modifiers |= Modifier.STRICT;
    }
    return modifiers;
  }

  @Override
  public int compareTo(MemberSignature other) {
    final int result = name.compareTo(other.name);
    if (result != 0) {
      return result;
    }
    return signature.compareTo(other.signature);
  }

  @Override
  public boolean equals(Object object) {
    try {
      final MemberSignature other = (MemberSignature)object;
      return name.equals(other.name) &&
             signature.equals(other.signature) &&
             modifiers == other.modifiers;
    }
    catch (ClassCastException | NullPointerException ignored) {
      return false;
    }
  }

  public static MemberSignature getAssertionsDisabledFieldMemberSignature() {
    return ASSERTIONS_DISABLED_FIELD;
  }

  public int getModifiers() {
    return modifiers;
  }

  public String getName() {
    return name;
  }

  public static MemberSignature getPackagePrivateConstructor() {
    return PACKAGE_PRIVATE_CONSTRUCTOR;
  }

  public static MemberSignature getPublicConstructor() {
    return PUBLIC_CONSTRUCTOR;
  }

  public String getSignature() {
    return signature;
  }

  public static MemberSignature getStaticInitializerMemberSignature() {
    return STATIC_INITIALIZER;
  }

  @Override
  public int hashCode() {
    return name.hashCode() + signature.hashCode();
  }

  @Override
  public String toString() {
    return name + signature;
  }
}