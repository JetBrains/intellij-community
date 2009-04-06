/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupActionProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElementAction;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.util.Consumer;

/**
 * @author peter
 */
public class ExcludeFromCompletionLookupActionProvider implements LookupActionProvider {
  public void fillActions(LookupElement element, Lookup lookup, Consumer<LookupElementAction> consumer) {
    if (element instanceof JavaPsiClassReferenceElement) {
      for (final String s : AddImportAction.getAllExcludableStrings(((JavaPsiClassReferenceElement)element).getQualifiedName())) {
        consumer.consume(new ExcludeFromCompletionAction(s));
      }
    }
  }

  private static class ExcludeFromCompletionAction extends LookupElementAction {
    private final String myToExclude;

    public ExcludeFromCompletionAction(String s) {
      super(null, "Exclude '" + s + "' from completion");
      myToExclude = s;
    }

    @Override
    public void performLookupAction() {
      AddImportAction.excludeFromImport(myToExclude);
    }
  }
}
