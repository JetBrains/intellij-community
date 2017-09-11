/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.lang.jvm.types.JvmPrimitiveType;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Represents primitive types of Java language.
 */
public class PsiPrimitiveType extends PsiType.Stub implements JvmPrimitiveType {

  private static final Map<String, PsiPrimitiveType> ourQNameToUnboxed = new THashMap<>();
  private static final Map<PsiPrimitiveType, String> ourUnboxedToQName = new THashMap<>();

  private final String myName;

  PsiPrimitiveType(@NotNull String name, String boxedName) {
    this(name, TypeAnnotationProvider.EMPTY);
    if (boxedName != null) {
      ourQNameToUnboxed.put(boxedName, this);
      ourUnboxedToQName.put(this, boxedName);
    }
  }

  public PsiPrimitiveType(@NotNull String name, @NotNull PsiAnnotation[] annotations) {
    super(annotations);
    myName = name;
  }

  public PsiPrimitiveType(@NotNull String name, @NotNull TypeAnnotationProvider provider) {
    super(provider);
    myName = name;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public PsiPrimitiveType annotate(@NotNull TypeAnnotationProvider provider) {
    return (PsiPrimitiveType)super.annotate(provider);
  }

  @NotNull
  @Override
  public String getPresentableText(boolean annotated) {
    return getText(false, annotated);
  }

  @NotNull
  @Override
  public String getCanonicalText(boolean annotated) {
    return getText(true, annotated);
  }

  @NotNull
  @Override
  public String getInternalCanonicalText() {
    return getCanonicalText(true);
  }

  private String getText(boolean qualified, boolean annotated) {
    PsiAnnotation[] annotations = annotated ? getAnnotations() : PsiAnnotation.EMPTY_ARRAY;
    if (annotations.length == 0) return myName;

    StringBuilder sb = new StringBuilder();
    PsiNameHelper.appendAnnotations(sb, annotations, qualified);
    sb.append(myName);
    return sb.toString();
  }

  /**
   * Always returns true.
   */
  @Override
  public boolean isValid() {
    for (PsiAnnotation annotation : getAnnotations()) {
      if (!annotation.isValid()) return false;
    }
    return true;
  }

  @Override
  public boolean equalsToText(@NotNull String text) {
    return myName.equals(text);
  }

  @Override
  public <A> A accept(@NotNull PsiTypeVisitor<A> visitor) {
    return visitor.visitPrimitiveType(this);
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return null;
  }

  @Override
  @NotNull
  public PsiType[] getSuperTypes() {
    return EMPTY_ARRAY;
  }

  /**
   * Returns the primitive type corresponding to a boxed class type.
   *
   * @param type the type to get the unboxed primitive type for.
   * @return the primitive type, or null if the type does not represent a boxed primitive type.
   */
  @Nullable
  public static PsiPrimitiveType getUnboxedType(PsiType type) {
    if (!(type instanceof PsiClassType)) return null;

    PsiUtil.ensureValidType(type);
    LanguageLevel languageLevel = ((PsiClassType)type).getLanguageLevel();
    if (!languageLevel.isAtLeast(LanguageLevel.JDK_1_5)) return null;

    PsiClass psiClass = ((PsiClassType)type).resolve();
    if (psiClass == null) return null;

    PsiPrimitiveType unboxed = ourQNameToUnboxed.get(psiClass.getQualifiedName());
    if (unboxed == null) return null;

    return unboxed.annotate(type.getAnnotationProvider());
  }

  @Nullable
  public static PsiPrimitiveType getOptionallyUnboxedType(PsiType type) {
    return type instanceof PsiPrimitiveType ? (PsiPrimitiveType)type : getUnboxedType(type);
  }

  public String getBoxedTypeName() {
    return ourUnboxedToQName.get(this);
  }

  /**
   * Returns a boxed class type corresponding to the primitive type.
   *
   * @param context where this boxed type is to be used
   * @return the class type, or null if the current language level does not support autoboxing or
   *         it was not possible to resolve the reference to the class.
   */
  @Nullable
  public PsiClassType getBoxedType(@NotNull PsiElement context) {
    PsiFile file = context.getContainingFile();
    if (file == null) return null;
    LanguageLevel languageLevel = PsiUtil.getLanguageLevel(file);
    if (!languageLevel.isAtLeast(LanguageLevel.JDK_1_5)) return null;

    String boxedQName = getBoxedTypeName();
    if (boxedQName == null) return null;

    JavaPsiFacade facade = JavaPsiFacade.getInstance(file.getProject());
    PsiClass aClass = facade.findClass(boxedQName, file.getResolveScope());
    if (aClass == null) return null;

    PsiElementFactory factory = facade.getElementFactory();
    return factory.createType(aClass, PsiSubstitutor.EMPTY, languageLevel).annotate(getAnnotationProvider());
  }

  @Nullable
  public PsiClassType getBoxedType(@NotNull PsiManager manager, @NotNull GlobalSearchScope resolveScope) {
    String boxedQName = getBoxedTypeName();
    if (boxedQName == null) return null;

    PsiClass aClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(boxedQName, resolveScope);
    if (aClass == null) return null;

    return JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createType(aClass);
  }

  public static Collection<String> getAllBoxedTypeNames() {
    return Collections.unmodifiableCollection(ourQNameToUnboxed.keySet());
  }

  @Override
  public int hashCode() {
    return myName.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof PsiPrimitiveType && myName.equals(((PsiPrimitiveType)obj).myName);
  }
}