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
  private final PsiManager myManager;
  private final PsiType myBound;
  private final boolean myIsExtending;
  @NonNls private static final String EXTENDS_PREFIX = "? extends ";
  @NonNls private static final String SUPER_PREFIX = "? super ";

  private PsiWildcardType(@NotNull PsiManager manager, boolean isExtending, PsiType bound) {
    super(PsiAnnotation.EMPTY_ARRAY);//todo
    myManager = manager;
    myIsExtending = isExtending;
    myBound = bound;
  }

  @NotNull
  public static PsiWildcardType createUnbounded(PsiManager manager) {
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
    return new PsiWildcardType(manager, false, bound);
  }

  public static PsiWildcardType changeBound(@NotNull PsiWildcardType type, @NotNull PsiType newBound) {
    LOG.assertTrue(type.getBound() != null);
    return new PsiWildcardType(type.myManager, type.myIsExtending, newBound);
  }

  public String getPresentableText() {
    if (myBound == null) return "?";
    if (myIsExtending) {
      return EXTENDS_PREFIX + myBound.getPresentableText();
    }
    else {
      //noinspection HardCodedStringLiteral
      return "? super " + myBound.getPresentableText();
    }
  }

  public String getCanonicalText() {
    if (myBound == null) return "?";
    if (myIsExtending) {
      return EXTENDS_PREFIX + myBound.getCanonicalText();
    }
    else {
      //noinspection HardCodedStringLiteral
      return "? super " + myBound.getCanonicalText();
    }
  }

  public String getInternalCanonicalText() {
    if (myBound == null) return "?";
    if (myIsExtending) {
      return EXTENDS_PREFIX + myBound.getInternalCanonicalText();
    }
    else {
      //noinspection HardCodedStringLiteral
      return "? super " + myBound.getInternalCanonicalText();
    }
  }


  public GlobalSearchScope getResolveScope() {
    if (myBound != null) {
      return myBound.getResolveScope();
    }
    else {
      return GlobalSearchScope.allScope(myManager.getProject());
    }
  }

  @NotNull
  public PsiType[] getSuperTypes() {
    return new PsiType[]{getExtendsBound()};
  }

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
      return that.isExtends() && that.myBound.equalsToText("java.lang.Object");
    }
    else if (myBound != null && that.myBound == null) {
      return isExtends() && myBound.equalsToText("java.lang.Object");
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

  public <A> A accept(PsiTypeVisitor<A> visitor) {
    return visitor.visitWildcardType(this);
  }

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
