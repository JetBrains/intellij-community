/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public abstract class JavaSmartCompletionContributor extends AbstractCompletionContributor<JavaSmartCompletionParameters> {
  public static final ExtensionPointName<JavaSmartCompletionContributor> EP_NAME = ExtensionPointName.create("com.intellij.java.smartCompletionContributor");

  private final List<Pair<ElementPattern<? extends PsiElement>, CompletionProvider<JavaSmartCompletionParameters>>> myList =
      new ArrayList<Pair<ElementPattern<? extends PsiElement>, CompletionProvider<JavaSmartCompletionParameters>>>();

  public final void extend(final ElementPattern<? extends PsiElement> place, CompletionProvider<JavaSmartCompletionParameters> provider) {
    myList.add(new Pair<ElementPattern<? extends PsiElement>, CompletionProvider<JavaSmartCompletionParameters>>(place, provider));
  }

  public boolean fillCompletionVariants(final JavaSmartCompletionParameters parameters, CompletionResultSet result) {
    for (final Pair<ElementPattern<? extends PsiElement>, CompletionProvider<JavaSmartCompletionParameters>> pair : myList) {
      final ProcessingContext context = new ProcessingContext();
      if (ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        public Boolean compute() {
          return pair.first.accepts(parameters.getPosition(), context);
        }
      }).booleanValue()) {
        if (!pair.second.addCompletionVariants(parameters, context, result)) return false;
      }
    }
    return true;
  }




}
