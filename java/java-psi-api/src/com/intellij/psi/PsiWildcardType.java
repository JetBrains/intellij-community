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

import com.intellij.lang.jvm.types.JvmType;
import com.intellij.lang.jvm.types.JvmWildcardType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a wildcard type, with bounds.
 *
 * @author dsl
 */
public class PsiWildcardType extends PsiType.Stub implements JvmWildcardType {
  public static final String EXTENDS_PREFIX = "? extends ";
  public static final String SUPER_PREFIX = "? super ";

  private static final Logger LOG = Logger.getInstance(PsiWildcardType.class);
  private static final Key<PsiWildcardType> UNBOUNDED_WILDCARD = new Key<>("UNBOUNDED_WILDCARD");

  private final PsiManager myManager;
  private final boolean myIsExtending;
  private final PsiType myBound;

  private PsiWildcardType(@NotNull PsiManager manager, boolean isExtending, @Nullable PsiType bound) {
    super(TypeAnnotationProvider.EMPTY);
    myManager = manager;
    myIsExtending = isExtending;
    myBound = bound;
  }

  private PsiWildcardType(@NotNull PsiWildcardType type, @NotNull TypeAnnotationProvider provider) {
    super(provider);
    myManager = type.myManager;
    myIsExtending = type.myIsExtending;
    myBound = type.myBound;
  }

  @NotNull
  public static PsiWildcardType createUnbounded(@NotNull PsiManager manager) {
    PsiWildcardType unboundedWildcard = manager.getUserData(UNBOUNDED_WILDCARD);
    if (unboundedWildcard == null) {
      unboundedWildcard = manager.putUserDataIfAbsent(UNBOUNDED_WILDCARD, new PsiWildcardType(manager, false, null));
    }
    return unboundedWildcard;
  }

  @NotNull
  public static PsiWildcardType createExtends(@NotNull PsiManager manager, @NotNull PsiType bound) {
    LOG.assertTrue(!(bound instanceof PsiWildcardType));
    LOG.assertTrue(bound != PsiType.NULL);
    return new PsiWildcardType(manager, true, bound);
  }

  @NotNull
  public static PsiWildcardType createSuper(@NotNull PsiManager manager, @NotNull PsiType bound) {
    LOG.assertTrue(!(bound instanceof PsiWildcardType));
    LOG.assertTrue(bound != PsiType.NULL);
    return new PsiWildcardType(manager, false, bound);
  }

  /**
   * @deprecated use {@link #annotate(TypeAnnotationProvider)} (to be removed in IDEA 18)
   */
  public PsiWildcardType annotate(@NotNull final PsiAnnotation[] annotations) {
    return annotations.length == 0 ? this : new PsiWildcardType(this, TypeAnnotationProvider.Static.create(annotations));
  }

  @NotNull
  @Override
  public String getPresentableText(boolean annotated) {
    return getText(false, annotated, myBound == null ? null : myBound.getPresentableText());
  }

  @Override
  @NotNull
  public String getCanonicalText(boolean annotated) {
    return getText(true, annotated, myBound == null ? null : myBound.getCanonicalText(annotated));
  }

  @NotNull
  @Override
  public String getInternalCanonicalText() {
    return getText(true, true, myBound == null ? null : myBound.getInternalCanonicalText());
  }

  private String getText(boolean qualified, boolean annotated, @Nullable String suffix) {
    PsiAnnotation[] annotations = annotated ? getAnnotations() : PsiAnnotation.EMPTY_ARRAY;
    if (annotations.length == 0 && suffix == null) return "?";

    StringBuilder sb = new StringBuilder();
    if (annotated) {
      PsiNameHelper.appendAnnotations(sb, annotations, qualified);
    }
    if (suffix == null) {
      sb.append('?');
    }
    else {
      sb.append(myIsExtending ? EXTENDS_PREFIX : SUPER_PREFIX);
      sb.append(suffix);
    }
    return sb.toString();
  }

  @Override
  @NotNull
  public GlobalSearchScope getResolveScope() {
    if (myBound != null) {
      GlobalSearchScope scope = myBound.getResolveScope();
      if (scope != null) {
        return scope;
      }
    }
    return GlobalSearchScope.allScope(myManager.getProject());
  }

  @Override
  @NotNull
  public PsiType[] getSuperTypes() {
    return new PsiType[]{getExtendsBound()};
  }

  @Override
  public boolean equalsToText(@NotNull String text) {
    if (myBound == null) {
      return "?".equals(text);
    }
    else if (myIsExtending) {
      return text.startsWith(EXTENDS_PREFIX) && myBound.equalsToText(text.substring(EXTENDS_PREFIX.length()));
    }
    else {
      return text.startsWith(SUPER_PREFIX) && myBound.equalsToText(text.substring(SUPER_PREFIX.length()));
    }
  }

  @NotNull
  public PsiManager getManager() {
    return myManager;
  }

  public boolean equals(Object o) {
    if (!(o instanceof PsiWildcardType)) return false;

    PsiWildcardType that = (PsiWildcardType)o;
    if (myBound == null && that.myBound != null) {
      return that.isExtends() && that.myBound.equalsToText(CommonClassNames.JAVA_LANG_OBJECT);
    }
    else if (myBound != null && that.myBound == null) {
      return isExtends() && myBound.equalsToText(CommonClassNames.JAVA_LANG_OBJECT);
    }
    return myIsExtending == that.myIsExtending && Comparing.equal(myBound, that.myBound);
  }

  public int hashCode() {
    return (myIsExtending ? 1 : 0) + (myBound != null ? myBound.hashCode() : 0);
  }

  /**
   * Use this method to obtain a bound of wildcard type.
   *
   * @return {@code null} if unbounded, a bound otherwise.
   */
  @Nullable
  public PsiType getBound() {
    return myBound;
  }

  @Override
  public <A> A accept(@NotNull PsiTypeVisitor<A> visitor) {
    return visitor.visitWildcardType(this);
  }

  @Override
  public boolean isValid() {
    return myBound == null || myBound.isValid();
  }

  /**
   * Returns whether this is a lower bound ({@code ? extends XXX}).
   *
   * @return {@code true} for {@code extends} wildcards, {@code false} for {@code super}
   * and unbounded wildcards.
   */
  public boolean isExtends() {
    return myBound != null && myIsExtending;
  }

  /**
   * Returns whether this is an upper bound ({@code ? super XXX}).
   *
   * @return {@code true} for {@code super} wildcards, {@code false} for {@code extends}
   * and unbounded wildcards.
   */
  public boolean isSuper() {
    return myBound != null && !myIsExtending;
  }

  /**
   * @return false for unbounded wildcards, true otherwise
   */
  public boolean isBounded() {
    return myBound != null;
  }

  /**
   * A lower bound that this wildcard imposes on type parameter value.<br>
   * That is:<br>
   * <ul>
   * <li> for {@code ? extends XXX}: {@code XXX}
   * <li> for {@code ? super XXX}: {@code java.lang.Object}
   * <li> for {@code ?}: {@code java.lang.Object}
   * </ul>
   *
   * @return {@code PsiType} representing a lower bound. Never returns {@code null}.
   */
  @NotNull
  public PsiType getExtendsBound() {
    if (myBound == null || !myIsExtending) {
      return getJavaLangObject(myManager, getResolveScope());
    }
    return myBound;
  }

  /**
   * An upper bound that this wildcard imposes on type parameter value.<br>
   * That is:<br>
   * <ul>
   * <li> for {@code ? extends XXX}: null type
   * <li> for {@code ? super XXX}: {@code XXX}
   * <li> for {@code ?}: null type
   * </ul>
   *
   * @return {@code PsiType} representing an upper bound. Never returns {@code null}.
   */
  @NotNull
  public PsiType getSuperBound() {
    return myBound == null || myIsExtending ? NULL : myBound;
  }

  @NotNull
  @Override
  public JvmType upperBound() {
    return getExtendsBound();
  }

  @NotNull
  @Override
  public JvmType lowerBound() {
    return getSuperBound();
  }
}