/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.util.Processor;
import gnu.trove.THashSet;

import java.util.Set;

/**
 * @author peter
 */
class LimitedAccessibleClassPreprocessor implements Processor<PsiClass> {
  private static final Logger LOG = Logger.getInstance(LimitedAccessibleClassPreprocessor.class);
  private final PsiElement myContext;
  private final CompletionParameters myParameters;
  private final boolean myFilterByScope;
  private final Processor<? super PsiClass> myProcessor;
  private final int myLimit = Registry.intValue("ide.completion.variant.limit");
  private int myCount;
  private final Set<String> myQNames = new THashSet<>();
  private final boolean myPkgContext;
  private final String myPackagePrefix;

  LimitedAccessibleClassPreprocessor(CompletionParameters parameters, boolean filterByScope, Processor<? super PsiClass> processor) {
    myContext = parameters.getPosition();
    myParameters = parameters;
    myFilterByScope = filterByScope;
    myProcessor = processor;
    myPkgContext = JavaCompletionUtil.inSomePackage(myContext);
    myPackagePrefix = getPackagePrefix(myContext, myParameters.getOffset());
  }

  private static String getPackagePrefix(final PsiElement context, final int offset) {
    final CharSequence fileText = context.getContainingFile().getViewProvider().getContents();
    int i = offset - 1;
    while (i >= 0) {
      final char c = fileText.charAt(i);
      if (!Character.isJavaIdentifierPart(c) && c != '.') break;
      i--;
    }
    String prefix = fileText.subSequence(i + 1, offset).toString();
    final int j = prefix.lastIndexOf('.');
    return j > 0 ? prefix.substring(0, j) : "";
  }

  @Override
  public boolean process(PsiClass psiClass) {
    if (myParameters.getInvocationCount() < 2) {
      if (PsiReferenceExpressionImpl.seemsScrambled(psiClass)) {
        return true;
      }
      String name = psiClass.getName();
      if (name != null && !name.isEmpty() && Character.isLowerCase(name.charAt(0)) &&
          !Registry.is("ide.completion.show.lower.case.classes")) {
        return true;
      }
    }

    assert psiClass != null;
    if (AllClassesGetter.isAcceptableInContext(myContext, psiClass, myFilterByScope, myPkgContext)) {
      String qName = psiClass.getQualifiedName();
      if (LOG.isDebugEnabled()) {
        LOG.debug("Processing class " + qName);
      }
      if (qName != null && qName.startsWith(myPackagePrefix) && myQNames.add(qName)) {
        if (!myProcessor.process(psiClass)) return false;
        if (++myCount > myLimit) {
          LOG.debug("Limit reached");
          return false;
        }
      }
    }
    return true;
  }
}
