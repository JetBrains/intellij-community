// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
public final class PsiWildcardType extends PsiType.Stub implements JvmWildcardType {
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

  public static @NotNull PsiWildcardType createUnbounded(@NotNull PsiManager manager) {
    PsiWildcardType unboundedWildcard = manager.getUserData(UNBOUNDED_WILDCARD);
    if (unboundedWildcard == null) {
      unboundedWildcard = manager.putUserDataIfAbsent(UNBOUNDED_WILDCARD, new PsiWildcardType(manager, false, null));
    }
    return unboundedWildcard;
  }

  public static @NotNull PsiWildcardType createExtends(@NotNull PsiManager manager, @NotNull PsiType bound) {
    LOG.assertTrue(!(bound instanceof PsiWildcardType) && bound != PsiType.NULL, bound);
    return new PsiWildcardType(manager, true, bound);
  }

  public static @NotNull PsiWildcardType createSuper(@NotNull PsiManager manager, @NotNull PsiType bound) {
    LOG.assertTrue(!(bound instanceof PsiWildcardType) && bound != PsiType.NULL, bound);
    return new PsiWildcardType(manager, false, bound);
  }

  @Override
  public @NotNull String getPresentableText(boolean annotated) {
    return getText(false, annotated, myBound == null ? null : myBound.getPresentableText(annotated));
  }

  @Override
  public @NotNull String getCanonicalText(boolean annotated) {
    return getText(true, annotated, myBound == null ? null : myBound.getCanonicalText(annotated));
  }

  @Override
  public @NotNull String getInternalCanonicalText() {
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
  public @NotNull GlobalSearchScope getResolveScope() {
    if (myBound != null) {
      GlobalSearchScope scope = myBound.getResolveScope();
      if (scope != null) {
        return scope;
      }
    }
    return GlobalSearchScope.allScope(myManager.getProject());
  }

  @Override
  public PsiType @NotNull [] getSuperTypes() {
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

  public @NotNull PsiManager getManager() {
    return myManager;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof PsiWildcardType)) return false;

    PsiWildcardType that = (PsiWildcardType)o;
    if (myBound == null && that.myBound != null) {
      return that.isExtends() && that.myBound.equalsToText(CommonClassNames.JAVA_LANG_OBJECT);
    }
    if (myBound != null && that.myBound == null) {
      return isExtends() && myBound.equalsToText(CommonClassNames.JAVA_LANG_OBJECT);
    }
    return myIsExtending == that.myIsExtending && Comparing.equal(myBound, that.myBound);
  }

  @Override
  public int hashCode() {
    return (myIsExtending ? 1 : 0) + (myBound != null ? myBound.hashCode() : 0);
  }

  /**
   * Use this method to obtain a bound of wildcard type.
   *
   * @return {@code null} if unbounded, a bound otherwise.
   */
  public @Nullable PsiType getBound() {
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
  public @NotNull PsiType getExtendsBound() {
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
  public @NotNull PsiType getSuperBound() {
    return myBound == null || myIsExtending ? NULL : myBound;
  }

  @Override
  public @NotNull JvmType upperBound() {
    return getExtendsBound();
  }

  @Override
  public @NotNull JvmType lowerBound() {
    return getSuperBound();
  }
}