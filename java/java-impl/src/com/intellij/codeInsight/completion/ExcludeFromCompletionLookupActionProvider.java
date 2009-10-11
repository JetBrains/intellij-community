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
