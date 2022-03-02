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
package com.intellij.refactoring.typeCook.deductive.builder;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.typeCook.deductive.resolver.Binding;

/**
 * @author db
 */
public abstract class Constraint {
  private static final Logger LOG = Logger.getInstance(Constraint.class);

  PsiType myLeft;
  PsiType myRight;

  public Constraint(PsiType left, PsiType right) {
    LOG.assertTrue(left != null, "<null> left type");
    LOG.assertTrue(right != null, "<null> right type");

    myLeft = left;
    myRight = right;
  }

  public PsiType getRight() {
    return myRight;
  }

  public PsiType getLeft() {
    return myLeft;
  }

  abstract String relationString();

  abstract int relationType();

  public String toString() {
    return myLeft.getCanonicalText() + " " + relationString() + " " + myRight.getCanonicalText();
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Constraint)) return false;

    final Constraint constraint = (Constraint)o;

    if (myLeft != null ? !myLeft.equals(constraint.myLeft) : constraint.myLeft != null) return false;
    if (myRight != null ? !myRight.equals(constraint.myRight) : constraint.myRight != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myLeft != null ? myLeft.hashCode() : 0);
    result = 29 * result + (myRight != null ? myRight.hashCode() : 0);
    return result + relationType();
  }

  public abstract Constraint apply(final Binding b);
}
