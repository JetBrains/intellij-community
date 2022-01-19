// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.light;

import com.intellij.model.BranchableSyntheticPsiElement;
import com.intellij.model.ModelBranch;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.IconManager;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.BitUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.VisibilityIcons;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

public class LightRecordCanonicalConstructor extends LightMethod implements SyntheticElement, BranchableSyntheticPsiElement {
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

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
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
      IconManager.getInstance().createLayeredIcon(this, PlatformIcons.METHOD_ICON, ElementPresentationUtil.getFlags(this, false));
    if (BitUtil.isSet(flags, ICON_FLAG_VISIBILITY)) {
      VisibilityIcons.setVisibilityIcon(getContainingClass().getModifierList(), baseIcon);
    }
    return baseIcon;
  }

  @Override
  public @NotNull LightRecordCanonicalConstructor obtainBranchCopy(@NotNull ModelBranch branch) {
    PsiClass recordCopy = branch.obtainPsiCopy(myContainingClass);
    PsiMethod accessorCopy = recordCopy.findMethodBySignature(this, false);
    assert accessorCopy instanceof LightRecordCanonicalConstructor;
    return (LightRecordCanonicalConstructor)accessorCopy;
  }
  
  @Override
  public @Nullable ModelBranch getModelBranch() {
    return ModelBranch.getPsiBranch(myContainingClass);
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

  public static class LightRecordConstructorParameter extends LightParameterWrapper {
    private final @NotNull LightParameterListWrapper myWrapper;

    private LightRecordConstructorParameter(@NotNull PsiParameter p,
                                            @NotNull LightParameterListWrapper wrapper,
                                            @NotNull PsiSubstitutor substitutor) {
      super(p, substitutor);
      myWrapper = wrapper;
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

      return new LightModifierList(getPrototype()) {
        @Override
        public PsiAnnotation @NotNull [] getAnnotations() {
          return recordComponent.getAnnotations();
        }
      };
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
  }
}
