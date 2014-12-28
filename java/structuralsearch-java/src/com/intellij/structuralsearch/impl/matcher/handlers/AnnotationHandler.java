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
package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.filters.AnnotationFilter;

/**
 * @author Bas
 */
public class AnnotationHandler extends MatchingHandler {

  public AnnotationHandler() {
    setFilter(AnnotationFilter.getInstance());
  }

  public boolean match(PsiElement patternNode, PsiElement matchedNode, MatchContext context) {
    if (!super.match(patternNode,matchedNode,context)) {
      return false;
    }

    final PsiElement element = patternNode.getFirstChild().getFirstChild();
    return context.getMatcher().match(element, matchedNode);
  }
}
