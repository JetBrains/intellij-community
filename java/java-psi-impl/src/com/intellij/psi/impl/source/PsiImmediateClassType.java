/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.source;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 *  @author dsl
 */
public class PsiImmediateClassType extends PsiClassType.Stub {
  private final PsiClass myClass;
  private final PsiSubstitutor mySubstitutor;
  private final PsiManager myManager;
  private String myCanonicalText;
  private String myCanonicalTextAnnotated;
  private String myPresentableText;
  private String myInternalCanonicalText;

  private final ClassResolveResult myClassResolveResult = new ClassResolveResult() {
    @Override
    public PsiClass getElement() {
      return myClass;
    }

    @NotNull
    @Override
    public PsiSubstitutor getSubstitutor() {
      return mySubstitutor;
    }

    @Override
    public boolean isValidResult() {
      return true;
    }

    @Override
    public boolean isAccessible() {
      return true;
    }

    @Override
    public boolean isStaticsScopeCorrect() {
      return true;
    }

    @Override
    public PsiElement getCurrentFileResolveScope() {
      return null;
    }

    @Override
    public boolean isPackagePrefixPackageReference() {
      return false;
    }
  };

  public PsiImmediateClassType(@NotNull PsiClass aClass, @NotNull PsiSubstitutor substitutor) {
    this(aClass, substitutor, null, TypeAnnotationProvider.EMPTY);
  }

  public PsiImmediateClassType(@NotNull PsiClass aClass, @NotNull PsiSubstitutor substitutor, @Nullable LanguageLevel level) {
    this(aClass, substitutor, level, TypeAnnotationProvider.EMPTY);
  }

  public PsiImmediateClassType(@NotNull PsiClass aClass,
                               @NotNull PsiSubstitutor substitutor,
                               @Nullable LanguageLevel level,
                               @NotNull PsiAnnotation... annotations) {
    super(level, annotations);
    myClass = aClass;
    myManager = aClass.getManager();
    mySubstitutor = substitutor;
    assert substitutor.isValid();
  }

  public PsiImmediateClassType(@NotNull PsiClass aClass,
                               @NotNull PsiSubstitutor substitutor,
                               @Nullable LanguageLevel level,
                               @NotNull TypeAnnotationProvider provider) {
    super(level, provider);
    myClass = aClass;
    myManager = aClass.getManager();
    mySubstitutor = substitutor;
    assert substitutor.isValid();
  }

  @Override
  public PsiClass resolve() {
    return myClass;
  }

  @Override
  public String getClassName() {
    return myClass.getName();
  }

  @Override
  @NotNull
  public PsiType[] getParameters() {
    final PsiTypeParameter[] parameters = myClass.getTypeParameters();
    if (parameters.length == 0) {
      return PsiType.EMPTY_ARRAY;
    }

    List<PsiType> lst = new ArrayList<>();
    for (PsiTypeParameter parameter : parameters) {
      PsiType substituted = mySubstitutor.substitute(parameter);
      if (substituted == null) {
        return PsiType.EMPTY_ARRAY;
      }
      lst.add(substituted);
    }
    return lst.toArray(createArray(lst.size()));
  }

  @Override
  @NotNull
  public ClassResolveResult resolveGenerics() {
    return myClassResolveResult;
  }

  @Override
  @NotNull
  public PsiClassType rawType() {
    return JavaPsiFacade.getInstance(myClass.getProject()).getElementFactory().createType(myClass);
  }

  @NotNull
  @Override
  public String getPresentableText(boolean annotated) {
    if (myPresentableText == null) {
      myPresentableText = getText(TextType.PRESENTABLE, annotated);
    }
    return myPresentableText;
  }

  @NotNull
  @Override
  public String getCanonicalText(boolean annotated) {
    String cached = annotated ? myCanonicalTextAnnotated : myCanonicalText;
    if (cached == null) {
      cached = getText(TextType.CANONICAL, annotated);
      if (annotated) myCanonicalTextAnnotated = cached;
      else myCanonicalText = cached;
    }
    return cached;
  }

  @NotNull
  @Override
  public String getInternalCanonicalText() {
    if (myInternalCanonicalText == null) {
      myInternalCanonicalText = getText(TextType.INT_CANONICAL, true);
    }
    return myInternalCanonicalText;
  }

  private enum TextType { PRESENTABLE, CANONICAL, INT_CANONICAL }

  private String getText(@NotNull TextType textType, boolean annotated) {
    mySubstitutor.ensureValid();
    StringBuilder buffer = new StringBuilder();
    buildText(myClass, mySubstitutor, buffer, textType, annotated);
    return buffer.toString();
  }

  private void buildText(@NotNull PsiClass aClass,
                         @NotNull PsiSubstitutor substitutor,
                         @NotNull StringBuilder buffer,
                         @NotNull TextType textType,
                         boolean annotated) {
    if (aClass instanceof PsiAnonymousClass) {
      ClassResolveResult baseResolveResult = ((PsiAnonymousClass)aClass).getBaseClassType().resolveGenerics();
      PsiClass baseClass = baseResolveResult.getElement();
      if (textType == TextType.INT_CANONICAL) {
        buffer.append("anonymous ");
      }
      if (baseClass != null) {
        buildText(baseClass, baseResolveResult.getSubstitutor(), buffer, textType, false);
      }
      else {
        buffer.append(((PsiAnonymousClass)aClass).getBaseClassReference().getCanonicalText());
      }
      return;
    }

    boolean qualified = textType != TextType.PRESENTABLE;

    PsiClass enclosingClass = null;
    if (!aClass.hasModifierProperty(PsiModifier.STATIC)) {
      PsiElement parent = aClass.getParent();
      if (parent instanceof PsiClass && !(parent instanceof PsiAnonymousClass)) {
        enclosingClass = (PsiClass)parent;
      }
    }
    if (enclosingClass != null) {
      buildText(enclosingClass, substitutor, buffer, textType, false);
      buffer.append('.');
    }
    else if (qualified) {
      String fqn = aClass.getQualifiedName();
      if (fqn != null) {
        String prefix = StringUtil.getPackageName(fqn);
        if (!StringUtil.isEmpty(prefix)) {
          buffer.append(prefix);
          buffer.append('.');
        }
      }
    }

    if (annotated) {
      PsiNameHelper.appendAnnotations(buffer, getAnnotations(), qualified);
    }

    buffer.append(aClass.getName());

    PsiTypeParameter[] typeParameters = aClass.getTypeParameters();
    if (typeParameters.length > 0) {
      int pos = buffer.length();
      buffer.append('<');

      for (int i = 0; i < typeParameters.length; i++) {
        PsiTypeParameter typeParameter = typeParameters[i];
        PsiUtilCore.ensureValid(typeParameter);

        if (i > 0) {
          buffer.append(',');
          if (textType == TextType.PRESENTABLE) buffer.append(' ');
        }

        PsiType substitutionResult = substitutor.substitute(typeParameter);
        if (substitutionResult == null) {
          buffer.setLength(pos);
          pos = -1;
          break;
        }
        PsiUtil.ensureValidType(substitutionResult);

        if (textType == TextType.PRESENTABLE) {
          buffer.append(substitutionResult.getPresentableText());
        }
        else if (textType == TextType.CANONICAL) {
          buffer.append(substitutionResult.getCanonicalText(annotated));
        }
        else {
          buffer.append(substitutionResult.getInternalCanonicalText());
        }
      }

      if (pos >= 0) {
        buffer.append('>');
      }
    }
  }

  @Override
  public boolean isValid() {
    for (PsiAnnotation annotation : getAnnotations()) {
      if (!annotation.isValid()) return false;
    }
    return myClass.isValid() && mySubstitutor.isValid();
  }

  @Override
  public boolean equalsToText(@NotNull String text) {
    String name = myClass.getName();
    if (name == null || !text.contains(name)) return false;
    if (text.equals(getCanonicalText(false))) return true;

    PsiElementFactory factory = JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory();
    final PsiType patternType;
    try {
      patternType = factory.createTypeFromText(text, myClass);
    }
    catch (IncorrectOperationException e) {
      return false;
    }
    return equals(patternType);
  }

  @Override
  @NotNull
  public GlobalSearchScope getResolveScope() {
    return myClass.getResolveScope();
  }

  @Override
  @NotNull
  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel != null ? myLanguageLevel : PsiUtil.getLanguageLevel(myClass);
  }

  @NotNull
  @Override
  public PsiClassType setLanguageLevel(@NotNull LanguageLevel level) {
    return level.equals(myLanguageLevel) ? this : new PsiImmediateClassType(myClass, mySubstitutor, level, getAnnotationProvider());
  }
}