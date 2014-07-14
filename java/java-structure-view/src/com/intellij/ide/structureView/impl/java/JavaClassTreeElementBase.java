/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.navigation.ColoredItemPresentation;
import com.intellij.navigation.LocationPresentation;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ui.UIUtil;

public abstract class JavaClassTreeElementBase<Value extends PsiElement> extends PsiTreeElementBase<Value> implements AccessLevelProvider,
                                                                                                                      ColoredItemPresentation,
                                                                                                                      LocationPresentation {
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
    return !(element instanceof PsiModifierListOwner) || ((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.PUBLIC);
  }

  @Override
  public int getAccessLevel() {
    final PsiModifierList modifierList = ((PsiModifierListOwner)getElement()).getModifierList();
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
        if (element instanceof PsiMember) {
          final PsiClass cls = ((PsiMember)element).getContainingClass();
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

  public boolean equals(final Object o) {
    if (!super.equals(o)) return false;
    final JavaClassTreeElementBase that = (JavaClassTreeElementBase)o;

    if (myIsInherited != that.myIsInherited) return false;

    return true;
  }

  @Override
  public TextAttributesKey getTextAttributesKey() {
    return isDeprecated() ? CodeInsightColors.DEPRECATED_ATTRIBUTES : null;
  }

  private boolean isDeprecated(){
    final Value element = getElement();
    return element instanceof PsiDocCommentOwner && ((PsiDocCommentOwner)element).isDeprecated();
 }
}
