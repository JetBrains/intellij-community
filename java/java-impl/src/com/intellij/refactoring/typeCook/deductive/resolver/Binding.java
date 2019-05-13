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
package com.intellij.refactoring.typeCook.deductive.resolver;

import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeVariable;

import java.util.Set;

/**
 * @author db
 */
public abstract class Binding {
  public abstract PsiType apply(PsiType type);

  public abstract PsiType substitute(PsiType type);

  abstract Binding compose(Binding b);

  final static int BETTER = 0;
  final static int WORSE = 1;
  final static int SAME = 2;
  final static int NONCOMPARABLE = 3;

  abstract int compare(Binding b);

  public abstract boolean nonEmpty();

  public abstract boolean isCyclic();

  public abstract Binding reduceRecursive();

  public abstract boolean binds(final PsiTypeVariable var);

  public abstract void merge(Binding b, boolean removeObject);

  public abstract Set<PsiTypeVariable> getBoundVariables();

  public abstract int getWidth();

  public abstract boolean isValid();

  public abstract void addTypeVariable (PsiTypeVariable var);
}
