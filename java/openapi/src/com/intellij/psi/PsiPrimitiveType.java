/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Represents primitive types of Java language.
 */
public class PsiPrimitiveType extends PsiType {
  private final String myName;

  public PsiPrimitiveType(@NonNls @NotNull String name, PsiAnnotation[] annotations) {
    super(annotations);
    myName = name;
  }

  @NonNls
  private static final Map<String, PsiPrimitiveType> ourQNameToUnboxed = new THashMap<String, PsiPrimitiveType>();
  @NonNls
  private static final Map<PsiPrimitiveType, String> ourUnboxedToQName = new THashMap<PsiPrimitiveType, String>();
  //registering ctor
  PsiPrimitiveType(@NonNls @NotNull String name, @NonNls String boxedName) {
    this(name, PsiAnnotation.EMPTY_ARRAY);
    if (boxedName != null) {
      ourQNameToUnboxed.put(boxedName, this);
      ourUnboxedToQName.put(this, boxedName);
    }
  }

  public String getPresentableText() {
    return myName;
  }

  public String getCanonicalText() {
    return myName;
  }

  public String getInternalCanonicalText() {
    return getAnnotationsTextPrefix() + getCanonicalText();
  }

  /**
   * Always returns true.
   */
  public boolean isValid() {
    return true;
  }

  public boolean equalsToText(String text) {
    return myName.equals(text);
  }

  public <A> A accept(PsiTypeVisitor<A> visitor) {
    return visitor.visitPrimitiveType(this);
  }

  public GlobalSearchScope getResolveScope() {
    return null;
  }

  @NotNull
  public PsiType[] getSuperTypes() {
    return new PsiType[0];
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
    if (!((PsiClassType)type).getLanguageLevel().hasEnumKeywordAndAutoboxing()) return null;
    final PsiClass psiClass = ((PsiClassType)type).resolve();
    if (psiClass == null) return null;
    return ourQNameToUnboxed.get(psiClass.getQualifiedName());
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
  public PsiClassType getBoxedType(PsiElement context) {
    LanguageLevel languageLevel = PsiUtil.getLanguageLevel(context);
    if (!languageLevel.hasEnumKeywordAndAutoboxing()) return null;
    final String boxedQName = getBoxedTypeName();

    //[ven]previous call returns null for NULL, VOID
    if (boxedQName == null) return null;

    PsiManager manager = context.getManager();
    final PsiClass aClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(boxedQName, context.getResolveScope());
    if (aClass == null) return null;
    return JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createType(aClass, PsiSubstitutor.EMPTY, languageLevel);
  }

  @Nullable
  public PsiClassType getBoxedType(final PsiManager manager, final GlobalSearchScope resolveScope) {
    final String boxedQName = getBoxedTypeName();

    //[ven]previous call returns null for NULL, VOID
    if (boxedQName == null) return null;

    final PsiClass aClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(boxedQName, resolveScope);
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
