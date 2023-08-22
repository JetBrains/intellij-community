// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.util.JavaAnonymousClassesHelper;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public final class JavaAnonymousClassTreeElement extends JavaClassTreeElement {
  public static final JavaAnonymousClassTreeElement[] EMPTY_ARRAY = {};

  private String myName;
  private String myBaseName;
  
  public JavaAnonymousClassTreeElement(PsiAnonymousClass aClass) {
    super(aClass, false);
  }

  @Override
  public boolean isPublic() {
    return false;
  }

  @Override
  public String getPresentableText() {
    if (myName != null) return myName;
    final PsiClass element = getElement();

    if (element != null) {
      myName = JavaAnonymousClassesHelper.getName((PsiAnonymousClass)element);
      if (myName != null) return myName;
    }
    return "Anonymous";
  }


  @Override
  public boolean isSearchInLocationString() {
    return true;
  }

  @Override
  public String getLocationString() {
    if (myBaseName == null) {
      PsiAnonymousClass anonymousClass = (PsiAnonymousClass)getElement();
      if (anonymousClass != null) {
        myBaseName = anonymousClass.getBaseClassType().getClassName();
      }
    }
    return myBaseName;
  }

  @Override
  public String toString() {
    return super.toString() + (myBaseName == null ? "" : " (" + getLocationString() + ")");
  }

  @Override
  public Icon getIcon(boolean open) {
    return IconManager.getInstance().getPlatformIcon(PlatformIcons.AnonymousClass);
  }
}
