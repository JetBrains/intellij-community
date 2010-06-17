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

package com.intellij.refactoring.inline;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.lang.Language;
import com.intellij.lang.refactoring.InlineHandler;
import com.intellij.lang.refactoring.InlineHandlers;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;

import java.util.*;

/**
 * @author ven
 */
@SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
public class GenericInlineHandler {

  public static boolean invoke(final PsiElement element, final Editor editor, final InlineHandler languageSpecific) {
    final PsiReference invocationReference = TargetElementUtilBase.findReference(editor);
    final InlineHandler.Settings settings = languageSpecific.prepareInlineElement(element, editor, invocationReference != null);
    if (settings == null || settings == InlineHandler.Settings.CANNOT_INLINE_SETTINGS) {
      return settings != null;
    }

    final Collection<PsiReference> allReferences =
      settings.isOnlyOneReferenceToInline() ? Collections.singleton(invocationReference) : ReferencesSearch.search(element).findAll();
    final Map<Language, InlineHandler.Inliner> inliners = new HashMap<Language, InlineHandler.Inliner>();

    final MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();
    for (PsiReference ref : allReferences) {
      final Language language = ref.getElement().getLanguage();
      if (inliners.containsKey(language)) continue;

      InlineHandler.Inliner inliner = null;
      final List<InlineHandler> handlers = InlineHandlers.getInlineHandlers(language);
      for (InlineHandler handler : handlers) {
        inliner = handler.createInliner(element, settings);
        if (inliner != null) {
          inliners.put(language, inliner);
          break;
        }
      }
      if (inliner == null) {
        conflicts.putValue(null, "Cannot inline reference from " + language.getID());
      }
    }

    for (PsiReference reference : allReferences) {
      collectConflicts(reference, element, inliners, conflicts);
    }

    final Project project = element.getProject();
    if (!conflicts.isEmpty()) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        throw new BaseRefactoringProcessor.ConflictsInTestsException(conflicts.values());
      } else {
        final ConflictsDialog conflictsDialog = new ConflictsDialog(project, conflicts);
        conflictsDialog.show();
        if (!conflictsDialog.isOK()){
          return true;
        }
      }
    }

    HashSet<PsiElement> elements = new HashSet<PsiElement>();
    for (PsiReference reference : allReferences) {
      PsiElement refElement = reference.getElement();
      if (refElement != null) {
        elements.add(refElement);
      }
    }
    if (!settings.isOnlyOneReferenceToInline()) {
      elements.add(element);
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, elements)) {
      return true;
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final String subj = element instanceof PsiNamedElement ? ((PsiNamedElement)element).getName() : "element";

        CommandProcessor.getInstance().executeCommand(project, new Runnable() {
          public void run() {
            final PsiReference[] references = sortDepthFirstRightLeftOrder(allReferences);


            final UsageInfo[] usages = new UsageInfo[references.length];
            for (int i = 0; i < references.length; i++) {
              usages[i] = new UsageInfo(references[i]);
            }

            for (UsageInfo usage : usages) {
              inlineReference(usage, element, inliners);
            }

            if (!settings.isOnlyOneReferenceToInline()) {
              languageSpecific.removeDefinition(element, settings);
            }
          }
        }, RefactoringBundle.message("inline.command", subj), null);
      }
    });
    return true;
  }

  private static void collectConflicts(final PsiReference reference,
                                       final PsiElement element,
                                       final Map<Language, InlineHandler.Inliner> inliners,
                                       final MultiMap<PsiElement, String> conflicts) {
    final Language language = reference.getElement().getLanguage();
    final InlineHandler.Inliner inliner = inliners.get(language);
    if (inliner != null) {
      final MultiMap<PsiElement, String> refConflicts = inliner.getConflicts(reference, element);
      if (refConflicts != null) {
        for (PsiElement psiElement : refConflicts.keySet()) {
          conflicts.putValues(psiElement, refConflicts.get(psiElement));
        }
      }
    }
  }

  private static void inlineReference(final UsageInfo usage,
                                      final PsiElement element,
                                      final Map<Language, InlineHandler.Inliner> inliners) {
    final Language language = usage.getElement().getLanguage();
    final InlineHandler.Inliner inliner = inliners.get(language);
    if (inliner != null) {
      inliner.inlineUsage(usage, element);
    }
  }

  //order of usages across different files is irrelevant
  public static PsiReference[] sortDepthFirstRightLeftOrder(final Collection<PsiReference> allReferences) {
    final PsiReference[] usages = allReferences.toArray(new PsiReference[allReferences.size()]);
    Arrays.sort(usages, new Comparator<PsiReference>() {
      public int compare(final PsiReference usage1, final PsiReference usage2) {
        final PsiElement element1 = usage1.getElement();
        final PsiElement element2 = usage2.getElement();
        if (element1 == null || element2 == null) return 0;
        return element2.getTextRange().getStartOffset() - element1.getTextRange().getStartOffset();
      }
    });
    return usages;
  }

}
