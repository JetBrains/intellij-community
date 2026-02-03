// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.light;

import com.intellij.openapi.util.Key;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.SyntheticElement;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.BitUtil;
import com.intellij.util.VisibilityIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/// @see <a href="https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.8.9">JLS 8.8.9 Default Constructor</a>
public class LightDefaultConstructor extends LightMethod implements SyntheticElement {

  private static final Key<PsiMethod> DEFAULT_CONSTRUCTOR = new Key<>("default_constructor");

  private LightDefaultConstructor(@NotNull PsiMethod method, @NotNull PsiClass containingClass) {
    super(method.getManager(), method, containingClass);
  }

  @Override
  public PsiElement getParent() {
    return getContainingClass();
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return getContainingClass().getNameIdentifier();
  }

  @Override
  public int getTextOffset() {
    return getNavigationElement().getTextOffset();
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return getContainingClass();
  }

  @Override
  public PsiFile getContainingFile() {
    return getContainingClass().getContainingFile();
  }

  @Override
  public Icon getElementIcon(int flags) {
    Icon icon = IconManager.getInstance().getPlatformIcon(PlatformIcons.Method);
    final RowIcon baseIcon = IconManager.getInstance().createLayeredIcon(this, icon, ElementPresentationUtil.getFlags(this, false));
    if (BitUtil.isSet(flags, ICON_FLAG_VISIBILITY)) {
      VisibilityIcons.setVisibilityIcon(getContainingClass().getModifierList(), baseIcon);
    }
    return baseIcon;
  }

  @Override
  public boolean isDefaultConstructor() {
    return true;
  }

  @Override
  public String toString() {
    return "LightDefaultConstructor:" + getName();
  }

  @Nullable
  public static PsiMethod create(PsiClass aClass) {
    PsiMethod constructor = aClass.getUserData(DEFAULT_CONSTRUCTOR);
    if (constructor != null) return constructor;

    if (aClass.isInterface()
        || aClass.isRecord()
        || aClass instanceof PsiAnonymousClass
        || aClass instanceof PsiImplicitClass
        || aClass instanceof PsiTypeParameter 
        || aClass instanceof PsiCompiledElement // default constructor is physically present in compiled classes already
        || aClass.getContainingFile() instanceof PsiCompiledElement) {
      return null;
    }
    String className = aClass.getName();
    if (className == null || aClass.getConstructors().length > 0) return null;
    PsiMethod nonPhysical = JavaPsiFacade.getElementFactory(aClass.getProject()).createConstructor(className, aClass);
    PsiModifierList classModifierList = aClass.getModifierList();
    if (classModifierList != null) {
      String modifier = PsiUtil.getAccessModifier(PsiUtil.getAccessLevel(classModifierList));
      nonPhysical.getModifierList().setModifierProperty(modifier, true);
    }
    LightDefaultConstructor defaultConstructor = new LightDefaultConstructor(nonPhysical, aClass);
    aClass.putUserData(DEFAULT_CONSTRUCTOR, defaultConstructor);
    return defaultConstructor;
  }
}