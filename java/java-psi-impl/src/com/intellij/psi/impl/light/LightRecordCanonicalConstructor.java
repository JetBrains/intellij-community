// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.light;

import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.BitUtil;
import com.intellij.util.VisibilityIcons;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

public class LightRecordCanonicalConstructor extends LightMethod implements SyntheticElement {
  public LightRecordCanonicalConstructor(@NotNull PsiMethod method,
                                         @NotNull PsiClass containingClass) {
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

  @Override
  public @NotNull PsiElement getNavigationElement() {
    return getContainingClass();
  }

  @Override
  public PsiFile getContainingFile() {
    PsiClass containingClass = getContainingClass();
    return containingClass.getContainingFile();
  }

  @Override
  public Icon getElementIcon(final int flags) {
    final RowIcon baseIcon =
      IconManager.getInstance().createLayeredIcon(this, IconManager.getInstance().getPlatformIcon(PlatformIcons.Method), ElementPresentationUtil.getFlags(this, false));
    if (BitUtil.isSet(flags, ICON_FLAG_VISIBILITY)) {
      VisibilityIcons.setVisibilityIcon(getContainingClass().getModifierList(), baseIcon);
    }
    return baseIcon;
  }

  @Override
  public @NotNull PsiParameterList getParameterList() {
    PsiParameterList parameterList = super.getParameterList();
    return new LightParameterListWrapper(parameterList, this.mySubstitutor) {
      @Override
      public PsiElement getParent() {
        return LightRecordCanonicalConstructor.this;
      }

      @Override
      public PsiFile getContainingFile() {
        return getParent().getContainingFile();
      }

      @Override
      public PsiParameter @NotNull [] getParameters() {
        return ContainerUtil.map2Array(super.getParameters(), PsiParameter.class,
                                       p -> new LightRecordConstructorParameter(p, this, mySubstitutor));
      }
    };
  }

  @Override
  public @NotNull PsiElement findSameElementInCopy(@NotNull PsiFile copy) {
    PsiClass copyClass = PsiTreeUtil.findSameElementInCopy(myContainingClass, copy);
    return Objects.requireNonNull(JavaPsiRecordUtil.findCanonicalConstructor(copyClass));
  }

  public static class LightRecordConstructorParameter extends LightParameterWrapper {
    private final @NotNull LightParameterListWrapper myWrapper;

    private LightRecordConstructorParameter(@NotNull PsiParameter p,
                                            @NotNull LightParameterListWrapper wrapper,
                                            @NotNull PsiSubstitutor substitutor) {
      super(p, substitutor);
      myWrapper = wrapper;
    }

    @Override
    public @NotNull PsiElement getDeclarationScope() {
      return myWrapper.getParent();
    }

    public @Nullable PsiRecordComponent getRecordComponent() {
      PsiClass psiClass = PsiTreeUtil.getParentOfType(this, PsiClass.class);
      if (psiClass == null) return null;
      PsiRecordComponent[] recordComponents = psiClass.getRecordComponents();
      for (PsiRecordComponent recordComponent : recordComponents) {
        if (Objects.equals(recordComponent.getName(), this.getName())) {
          return recordComponent;
        }
      }
      return null;
    }

    @Override
    public @Nullable PsiModifierList getModifierList() {
      PsiRecordComponent recordComponent = getRecordComponent();
      if (recordComponent == null) return super.getModifierList();

      return new LightRecordComponentModifierList(this, getPrototype(), recordComponent);
    }

    @Override
    public @NotNull PsiElement getNavigationElement() {
      PsiRecordComponent recordComponent = getRecordComponent();
      if (recordComponent != null) return recordComponent;
      return super.getNavigationElement();
    }

    @Override
    public PsiElement getParent() {
      return myWrapper;
    }

    @Override
    public @Nullable Icon getIcon(int flags) {
      return getPrototype().getIcon(flags);
    }

    @Override
    public PsiFile getContainingFile() {
      return getParent().getContainingFile();
    }

    @Override
    public @NotNull PsiElement findSameElementInCopy(@NotNull PsiFile copy) {
      PsiParameterList parameterList = (PsiParameterList)getParent();
      int index = parameterList.getParameterIndex(this);
      PsiClass recordClass = PsiTreeUtil.getParentOfType(this, PsiClass.class);
      PsiClass copyClass = PsiTreeUtil.findSameElementInCopy(recordClass, copy);
      assert copyClass != null;
      PsiMethod copyConstructor = JavaPsiRecordUtil.findCanonicalConstructor(copyClass);
      assert copyConstructor != null;
      PsiParameter copyParameter = copyConstructor.getParameterList().getParameter(index);
      assert copyParameter != null;
      return copyParameter;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      LightRecordConstructorParameter parameter = (LightRecordConstructorParameter)o;

      return getPrototype().equals(parameter.getPrototype());
    }

    @Override
    public int hashCode() {
      return getPrototype().hashCode();
    }

    @Override
    public String toString() {
      return "LightRecordConstructorParameter(" + super.toString() + ")";
    }
  }

  @Override
  public String toString() {
    return "LightRecordCanonicalConstructor(" + super.toString() + ")";
  }
}
