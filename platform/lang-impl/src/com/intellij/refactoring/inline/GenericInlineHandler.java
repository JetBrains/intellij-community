/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.lang.Language;
import com.intellij.lang.refactoring.InlineHandler;
import com.intellij.lang.refactoring.InlineHandlers;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
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
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author ven
 */
@SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
public class GenericInlineHandler {

  private static final Logger LOG = Logger.getInstance(GenericInlineHandler.class);

  public static boolean invoke(final PsiElement element, @Nullable Editor editor, final InlineHandler languageSpecific) {
    final PsiReference invocationReference = editor != null ? TargetElementUtilBase.findReference(editor) : null;
    final InlineHandler.Settings settings = languageSpecific.prepareInlineElement(element, editor, invocationReference != null);
    if (settings == null || settings == InlineHandler.Settings.CANNOT_INLINE_SETTINGS) {
      return settings != null;
    }

    final Collection<? extends PsiReference> allReferences;

    if (settings.isOnlyOneReferenceToInline()) {
      allReferences = Collections.singleton(invocationReference);
    }
    else {
      final Ref<Collection<? extends PsiReference>> usagesRef = new Ref<Collection<? extends PsiReference>>();
      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        @Override
        public void run() {
          usagesRef.set(ReferencesSearch.search(element).findAll());
        }
      }, "Find Usages", false, element.getProject());
      allReferences = usagesRef.get();
    }

    final MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();
    final Map<Language, InlineHandler.Inliner> inliners = initializeInliners(element, settings, allReferences);

    for (PsiReference reference : allReferences) {
      collectConflicts(reference, element, inliners, conflicts);
    }

    final Project project = element.getProject();
    if (!conflicts.isEmpty()) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        throw new BaseRefactoringProcessor.ConflictsInTestsException(conflicts.values());
      }
      else {
        final ConflictsDialog conflictsDialog = new ConflictsDialog(project, conflicts);
        conflictsDialog.show();
        if (!conflictsDialog.isOK()) {
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

    if (!CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, elements, true)) {
      return true;
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final String subj = element instanceof PsiNamedElement ? ((PsiNamedElement)element).getName() : "element";

        CommandProcessor.getInstance().executeCommand(project, new Runnable() {
          @Override
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
        }, RefactoringBundle.message("inline.command", StringUtil.notNullize(subj, "<nameless>")), null);
      }
    });
    return true;
  }

  public static Map<Language, InlineHandler.Inliner> initializeInliners(PsiElement element,
                                                                        InlineHandler.Settings settings,
                                                                        Collection<? extends PsiReference> allReferences) {
    final Map<Language, InlineHandler.Inliner> inliners = new HashMap<Language, InlineHandler.Inliner>();
    for (PsiReference ref : allReferences) {
      if (ref == null) {
        LOG.error("element: " + element.getClass()+ ", allReferences contains null!");
        continue;
      }
      PsiElement refElement = ref.getElement();
      LOG.assertTrue(refElement != null, ref.getClass().getName());

      final Language language = refElement.getLanguage();
      if (inliners.containsKey(language)) continue;

      final List<InlineHandler> handlers = InlineHandlers.getInlineHandlers(language);
      for (InlineHandler handler : handlers) {
        InlineHandler.Inliner inliner = handler.createInliner(element, settings);
        if (inliner != null) {
          inliners.put(language, inliner);
          break;
        }
      }
    }
    return inliners;
  }

  public static void collectConflicts(final PsiReference reference,
                                      final PsiElement element,
                                      final Map<Language, InlineHandler.Inliner> inliners,
                                      final MultiMap<PsiElement, String> conflicts) {
    final PsiElement referenceElement = reference.getElement();
    if (referenceElement == null) return;
    final Language language = referenceElement.getLanguage();
    final InlineHandler.Inliner inliner = inliners.get(language);
    if (inliner != null) {
      final MultiMap<PsiElement, String> refConflicts = inliner.getConflicts(reference, element);
      if (refConflicts != null) {
        for (PsiElement psiElement : refConflicts.keySet()) {
          conflicts.putValues(psiElement, refConflicts.get(psiElement));
        }
      }
    }
    else {
      conflicts.putValue(referenceElement, "Cannot inline reference from " + language.getID());
    }
  }

  public static void inlineReference(final UsageInfo usage,
                                     final PsiElement element,
                                     final Map<Language, InlineHandler.Inliner> inliners) {
    PsiElement usageElement = usage.getElement();
    if (usageElement == null) return;
    final Language language = usageElement.getLanguage();
    final InlineHandler.Inliner inliner = inliners.get(language);
    if (inliner != null) {
      inliner.inlineUsage(usage, element);
    }
  }

  //order of usages across different files is irrelevant
  public static PsiReference[] sortDepthFirstRightLeftOrder(final Collection<? extends PsiReference> allReferences) {
    final PsiReference[] usages = allReferences.toArray(new PsiReference[allReferences.size()]);
    Arrays.sort(usages, new Comparator<PsiReference>() {
      @Override
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
