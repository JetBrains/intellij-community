/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupActionProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;

/**
 * @author peter
 */
public class ExcludeFromCompletionLookupActionProvider implements LookupActionProvider {
  public void fillActions(LookupElement element, Lookup lookup, Consumer<LookupElementAction> consumer) {
    if (element instanceof JavaPsiClassReferenceElement) {
      final JavaPsiClassReferenceElement referenceElement = (JavaPsiClassReferenceElement)element;
      final String qname = referenceElement.getQualifiedName();
      if (qname != null) {
        final Project project = referenceElement.getObject().getProject();
        for (final String s : AddImportAction.getAllExcludableStrings(qname)) {
          consumer.consume(new ExcludeFromCompletionAction(project, s));
        }
      }
    }
  }

  private static class ExcludeFromCompletionAction extends LookupElementAction {
    private final Project myProject;
    private final String myToExclude;

    public ExcludeFromCompletionAction(Project project, String s) {
      super(null, "Exclude '" + s + "' from completion");
      myProject = project;
      myToExclude = s;
    }

    @Override
    public void performLookupAction() {
      AddImportAction.excludeFromImport(myProject, myToExclude);
    }
  }
}
