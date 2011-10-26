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
package com.intellij.psi.filters.position;

import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;

/**
 * @author peter
 */
public class PatternFilter implements ElementFilter {
  private final ElementPattern<?> myPattern;

  public PatternFilter(final ElementPattern<?> pattern) {
    myPattern = pattern;
  }

  @Override
  public boolean isAcceptable(Object element, PsiElement context) {
    return myPattern.accepts(element);
  }

  @Override
  public boolean isClassAcceptable(Class hintClass) {
    return true;
    //throw new UnsupportedOperationException("Method isClassAcceptable is not yet implemented in " + getClass().getName());
  }

  public String toString() {
    return myPattern.toString();
  }
}
