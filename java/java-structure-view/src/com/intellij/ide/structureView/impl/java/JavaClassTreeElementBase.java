// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.navigation.ColoredItemPresentation;
import com.intellij.navigation.LocationPresentation;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ui.UIUtil;

public abstract class JavaClassTreeElementBase<Value extends PsiElement> extends PsiTreeElementBase<Value> 
  implements AccessLevelProvider, ColoredItemPresentation, LocationPresentation {

  private final boolean myIsInherited;
  protected String myLocation;

  protected JavaClassTreeElementBase(boolean isInherited, Value element) {
    super(element);
    myIsInherited = isInherited;
  }

  public boolean isInherited() {
    return myIsInherited;
  }

  public boolean isPublic() {
    Value element = getElement();
    return !(element instanceof PsiModifierListOwner owner) || owner.hasModifierProperty(PsiModifier.PUBLIC);
  }

  @Override
  public int getAccessLevel() {
    Value element = getElement();
    if (!(element instanceof PsiModifierListOwner owner)) return PsiUtil.ACCESS_LEVEL_PUBLIC;
    final PsiModifierList modifierList = owner.getModifierList();
    if (modifierList == null) {
      return PsiUtil.ACCESS_LEVEL_PUBLIC;
    }
    return PsiUtil.getAccessLevel(modifierList);
  }

  @Override
  public int getSubLevel() {
    return 0;
  }

  @Override
  public String getLocationString() {
    if (!Registry.is("show.method.base.class.in.java.file.structure")) return null;
    if (isInherited()) {
      if (myLocation == null) {
        final Value element = getElement();
        if (element instanceof PsiMember member) {
          final PsiClass cls = member.getContainingClass();
          if (cls == null) {
            myLocation = "";
          } else {
            myLocation = cls.getName();
            myLocation = UIUtil.rightArrow() + myLocation;
          }
        } else {
          myLocation = "";
        }
      }
      return StringUtil.isEmpty(myLocation) ? null : myLocation;
    }
    return super.getLocationString();
  }

  @Override
  public String getLocationPrefix() {
    return isInherited() ? " " : DEFAULT_LOCATION_PREFIX;
  }

  @Override
  public String getLocationSuffix() {
    return isInherited() ? "" : DEFAULT_LOCATION_SUFFIX;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;
    final JavaClassTreeElementBase<?> that = (JavaClassTreeElementBase<?>)o;

    return myIsInherited == that.myIsInherited;
  }

  @Override
  public TextAttributesKey getTextAttributesKey() {
    if (isInherited()) return CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES;
    try {
      return isDeprecated() ? CodeInsightColors.DEPRECATED_ATTRIBUTES : null;
    }
    catch (IndexNotReadyException ignore) {
      return null; // do not show deprecated elements during indexing
    }
  }

  private boolean isDeprecated() {
    return PsiImplUtil.isDeprecated(getElement());
 }
}
