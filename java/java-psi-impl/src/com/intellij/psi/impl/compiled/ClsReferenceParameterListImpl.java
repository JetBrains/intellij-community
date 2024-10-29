// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.cache.TypeAnnotationContainer;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClsReferenceParameterListImpl extends ClsElementImpl implements PsiReferenceParameterList {
  private static final @NonNls Pattern EXTENDS_PREFIX = Pattern.compile("^(\\?\\s*extends\\s*)(.*)");
  private static final @NonNls Pattern SUPER_PREFIX = Pattern.compile("^(\\?\\s*super\\s*)(.*)");

  private final @NotNull PsiElement myParent;
  private final ClsTypeElementImpl[] myTypeParameters;
  private volatile PsiType[] myTypeParametersCachedTypes;

  ClsReferenceParameterListImpl(@NotNull PsiElement parent,
                                @NotNull String @NotNull [] classParameters,
                                @NotNull TypeAnnotationContainer annotations) {
    myParent = parent;

    int length = classParameters.length;
    myTypeParameters = new ClsTypeElementImpl[length];

    for (int i = 0; i < length; i++) {
      String s = classParameters[i];
      char variance = ClsTypeElementImpl.VARIANCE_NONE;
      final Matcher extendsMatcher = EXTENDS_PREFIX.matcher(s);
      if (extendsMatcher.find()) {
        variance = ClsTypeElementImpl.VARIANCE_EXTENDS;
        s = extendsMatcher.group(2);
      }
      else {
        final Matcher superMatcher = SUPER_PREFIX.matcher(s);
        if (superMatcher.find()) {
          variance = ClsTypeElementImpl.VARIANCE_SUPER;
          s = superMatcher.group(2);
        }
        else if (StringUtil.startsWithChar(s, '?')) {
          variance = ClsTypeElementImpl.VARIANCE_INVARIANT;
          s = s.substring(1);
        }
      }

      myTypeParameters[i] = new ClsTypeElementImpl(this, s, variance, annotations.forTypeArgument(i));
    }
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitReferenceParameterList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) { }

  @Override
  protected void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, JavaElementType.REFERENCE_PARAMETER_LIST);

    PsiReferenceParameterList mirror = SourceTreeToPsiMap.treeToPsiNotNull(element);
    PsiTypeElement[] children = PsiTreeUtil.getChildrenOfType(mirror, PsiTypeElement.class);
    if (children != null) {
      setMirrors(myTypeParameters, children);
    }
  }

  @Override
  public PsiTypeElement @NotNull [] getTypeParameterElements() {
    return myTypeParameters;
  }

  @Override
  public PsiType @NotNull [] getTypeArguments() {
    PsiType[] cachedTypes = myTypeParametersCachedTypes;
    if (cachedTypes == null) {
      cachedTypes = PsiType.createArray(myTypeParameters.length);
      for (int i = 0; i < cachedTypes.length; i++) {
        cachedTypes[i] = myTypeParameters[i].getType();
      }
      myTypeParametersCachedTypes = cachedTypes;
    }
    return cachedTypes;
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    return myTypeParameters;
  }

  @Override
  public @NotNull PsiElement getParent() {
    return myParent;
  }
}
