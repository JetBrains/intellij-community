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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;

/**
 * @author peter
 */
public class ExcludeFromCompletionLookupActionProvider implements LookupActionProvider {
  public void fillActions(LookupElement element, Lookup lookup, Consumer<LookupElementAction> consumer) {
    final Object o = element.getObject();
    PsiClass clazz = null;
    if (o instanceof PsiType) {
      clazz = PsiUtil.resolveClassInType((PsiType)o);
    } else if (o instanceof PsiClass) {
      clazz = (PsiClass)o;
    }
    if (clazz != null && clazz.isValid()) {
      final String qname = clazz.getQualifiedName();
      if (qname != null) {
        final Project project = clazz.getProject();
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
