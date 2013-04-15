/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a wildcard type, with bounds.
 *
 * @author dsl
 */
public class PsiWildcardType extends PsiType {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.PsiWildcardType");

  private static final Key<PsiWildcardType> UNBOUNDED_WILDCARD = new Key<PsiWildcardType>("UNBOUNDED_WILDCARD");
  @NonNls private static final String EXTENDS_PREFIX = "? extends ";
  @NonNls private static final String SUPER_PREFIX = "? super ";

  private final PsiManager myManager;
  private final boolean myIsExtending;
  private final PsiType myBound;

  private PsiWildcardType(@NotNull PsiManager manager, boolean isExtending, @Nullable PsiType bound) {
    super(PsiAnnotation.EMPTY_ARRAY);
    myManager = manager;
    myIsExtending = isExtending;
    myBound = bound;
  }

  private PsiWildcardType(@NotNull PsiWildcardType type, @NotNull PsiAnnotation[] annotations) {
    super(annotations);
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
    return new PsiWildcardType(manager, true, bound);
  }

  @NotNull
  public static PsiWildcardType createSuper(@NotNull PsiManager manager, @NotNull PsiType bound) {
    LOG.assertTrue(!(bound instanceof PsiWildcardType));
    return new PsiWildcardType(manager, false, bound);
  }

  @NotNull
  public PsiWildcardType annotate(@NotNull PsiAnnotation[] annotations) {
    return annotations.length == 0 ? this : new PsiWildcardType(this, annotations);
  }

  /**
   * @deprecated implementation details (to remove in IDEA 13)
   */
  @SuppressWarnings("UnusedDeclaration")
  public static PsiWildcardType changeBound(@NotNull PsiWildcardType type, @NotNull PsiType newBound) {
    LOG.assertTrue(type.getBound() != null);
    LOG.assertTrue(newBound.isValid());
    if (type.myIsExtending) {
      if (newBound.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
        return createUnbounded(type.myManager);
      }
    }
    return new PsiWildcardType(type.myManager, type.myIsExtending, newBound);
  }

  @Override
  public String getPresentableText() {
    return getAnnotationsTextPrefix(false, false, true) +
           (myBound == null ? "?" : (myIsExtending ? EXTENDS_PREFIX : SUPER_PREFIX) + myBound.getPresentableText());
  }

  @Override
  public String getCanonicalText() {
    return (myBound == null ? "?" : (myIsExtending ? EXTENDS_PREFIX : SUPER_PREFIX) + myBound.getCanonicalText());
  }

  @Override
  public String getInternalCanonicalText() {
    return getAnnotationsTextPrefix(true, false, true) +
           (myBound == null ? "?" : (myIsExtending ? EXTENDS_PREFIX : SUPER_PREFIX) + myBound.getInternalCanonicalText());
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
  public boolean equalsToText(String text) {
    if (myBound == null) return "?".equals(text);
    if (myIsExtending) {
      return text.startsWith(EXTENDS_PREFIX) && myBound.equalsToText(text.substring(EXTENDS_PREFIX.length()));
    }
    else {
      return text.startsWith(SUPER_PREFIX) && myBound.equalsToText(text.substring(SUPER_PREFIX.length()));
    }
  }

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
   * @return <code>null</code> if unbounded, a bound otherwise.
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
   * Returns whether this is a lower bound (<code>? extends XXX</code>).
   *
   * @return <code>true</code> for <code>extends</code> wildcards, <code>false</code> for <code>super</code>
   *         and unbounded wildcards.
   */
  public boolean isExtends() {
    return myBound != null && myIsExtending;
  }

  /**
   * Returns whether this is an upper bound (<code>? super XXX</code>).
   *
   * @return <code>true</code> for <code>super</code> wildcards, <code>false</code> for <code>extends</code>
   *         and unbounded wildcards.
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
   * <li> for <code>? extends XXX</code>: <code>XXX</code>
   * <li> for <code>? super XXX</code>: <code>java.lang.Object</code>
   * <li> for <code>?</code>: <code>java.lang.Object</code>
   * </ul>
   *
   * @return <code>PsiType</code> representing a lower bound. Never returns <code>null</code>.
   */
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
   * <li> for <code>? extends XXX</code>: null type
   * <li> for <code>? super XXX</code>: <code>XXX</code>
   * <li> for <code>?</code>: null type
   * </ul>
   *
   * @return <code>PsiType</code> representing an upper bound. Never returns <code>null</code>.
   */
  public PsiType getSuperBound() {
    return myBound == null || myIsExtending ? NULL : myBound;
  }
}
