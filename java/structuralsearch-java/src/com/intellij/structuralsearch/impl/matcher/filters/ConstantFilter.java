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
package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Apr 27, 2004
 * Time: 7:55:40 PM
 * To change this template use File | Settings | File Templates.
 */
public class ConstantFilter implements NodeFilter {

  private static final NodeFilter INSTANCE = new ConstantFilter();

  private ConstantFilter() {}

  @Override
  public boolean accepts(PsiElement element) {
    return element instanceof PsiLiteralExpression;
  }

  public static NodeFilter getInstance() {
    return INSTANCE;
  }
}
