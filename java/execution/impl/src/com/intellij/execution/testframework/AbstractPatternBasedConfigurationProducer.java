// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework;

import com.intellij.execution.JavaTestConfigurationBase;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

public abstract class AbstractPatternBasedConfigurationProducer<T extends JavaTestConfigurationBase> extends AbstractJavaTestConfigurationProducer<T> implements Cloneable {
  /**
   * @deprecated Override {@link #getConfigurationFactory()}.
   */
  @Deprecated
  public AbstractPatternBasedConfigurationProducer(ConfigurationType configurationType) {
    super(configurationType);
  }

  protected AbstractPatternBasedConfigurationProducer() {
    super();
  }

  public boolean isConfiguredFromContext(ConfigurationContext context, Set<String> patterns) {
    final LinkedHashSet<String> classes = new LinkedHashSet<>();
    final DataContext dataContext = context.getDataContext();
    if (TestsUIUtil.isMultipleSelectionImpossible(dataContext)) {
      return false;
    }
    final PsiElement[] locationElements = collectLocationElements(classes, dataContext);
    if (locationElements == null) {
      collectContextElements(dataContext, true, false, classes, new PsiElementProcessor.CollectElements<>());
    }
    int patternsSize = patterns.size();
    if (patternsSize == classes.size()) {
      final Iterator<String> patternsIterator = patterns.iterator();
      final Iterator<String> classesIterator = classes.iterator();
      while (patternsIterator.hasNext() && classesIterator.hasNext()) {
        if (!Comparing.equal(patternsIterator.next(), classesIterator.next())) {
          return false;
        }
      }

      if (patternsSize == 1) {
        final String pattern = patterns.iterator().next();
        if (!pattern.contains(",")) {
          final PsiMethod method = PsiTreeUtil.getParentOfType(CommonDataKeys.PSI_ELEMENT.getData(dataContext), PsiMethod.class);
          return method != null && isTestMethod(false, method);
        }
      }
      return true;
    }
    return false;
  }


  public PsiElement checkPatterns(ConfigurationContext context, LinkedHashSet<String> classes) {
    PsiElement[] result;
    final DataContext dataContext = context.getDataContext();
    if (TestsUIUtil.isMultipleSelectionImpossible(dataContext)) {
      return null;
    }
    final PsiElement[] locationElements = collectLocationElements(classes, dataContext);
    PsiElementProcessor.CollectElements<PsiElement> processor = new PsiElementProcessor.CollectElements<>();
    if (locationElements != null) {
      collectTestMembers(locationElements, false, true, processor);
      result = processor.toArray();
    }
    else if (collectContextElements(dataContext, true, true, classes, processor)) {
      result = processor.toArray();
    }
    else {
      return null;
    }
    if (result.length <= 1) {
      return null;
    }
    return result[0];
  }


}
