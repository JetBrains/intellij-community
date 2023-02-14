// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.util.Processor;

import java.util.HashSet;
import java.util.Set;

final class LimitedAccessibleClassPreprocessor implements Processor<PsiClass> {
  private static final Logger LOG = Logger.getInstance(LimitedAccessibleClassPreprocessor.class);
  private final PsiElement myContext;
  private final CompletionParameters myParameters;
  private final boolean myFilterByScope;
  private final Processor<? super PsiClass> myProcessor;
  private final int myLimit = Registry.intValue("ide.completion.variant.limit");
  private int myCount;
  private final Set<String> myQNames = new HashSet<>();
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
      if (PsiReferenceExpressionImpl.seemsScrambled(psiClass) || JavaCompletionProcessor.seemsInternal(psiClass)) {
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
