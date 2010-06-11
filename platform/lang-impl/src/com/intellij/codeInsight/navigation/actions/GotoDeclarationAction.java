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

package com.intellij.codeInsight.navigation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;

public class GotoDeclarationAction extends BaseCodeInsightAction implements CodeInsightActionHandler, DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.navigation.actions.GotoDeclarationAction");
  protected CodeInsightActionHandler getHandler() {
    String s = "/java/lang/Object.class";
    return this;
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    return true;
  }

  protected boolean isValidForLookup() {
    return true;
  }

  public void invoke(@NotNull final Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    try {
      int offset = editor.getCaretModel().getOffset();
      PsiElement element = findTargetElement(project, editor, offset);
      FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.goto.declaration");
      if (element == null) {
        chooseAmbiguousTarget(editor, offset);
        return;
      }

      PsiElement navElement = element.getNavigationElement();
      navElement = TargetElementUtilBase.getInstance().getGotoDeclarationTarget(element, navElement);

      if (navElement instanceof Navigatable) {
        if (((Navigatable)navElement).canNavigate()) {
          ((Navigatable)navElement).navigate(true);
        }
      }
      else if (navElement != null) {
        int navOffset = navElement.getTextOffset();
        VirtualFile virtualFile = PsiUtilBase.getVirtualFile(navElement);
        if (virtualFile != null) {
          new OpenFileDescriptor(project, virtualFile, navOffset).navigate(true);
        }
      }
    }
    catch (IndexNotReadyException e) {
      DumbService.getInstance(project).showDumbModeNotification("Navigation is not available here during index update");
    }
  }

  private static void chooseAmbiguousTarget(final Editor editor, int offset) {
    PsiElementProcessor<PsiElement> navigateProcessor = new PsiElementProcessor<PsiElement>() {
      public boolean execute(final PsiElement element) {
        Navigatable navigatable = EditSourceUtil.getDescriptor(element);
        if (navigatable != null && navigatable.canNavigate()) {
          navigatable.navigate(true);
        }
        return true;
      }
    };
    boolean found = chooseAmbiguousTarget(editor, offset, navigateProcessor, CodeInsightBundle.message("declaration.navigation.title"));
    if (!found) {
      HintManager.getInstance().showErrorHint(editor, "Cannot find declaration to go to");
    }
  }

  // returns true if processor is run or is going to be run after showing popup
  public static boolean chooseAmbiguousTarget(final Editor editor, int offset, PsiElementProcessor<PsiElement> processor, String titlePattern) {
    if (TargetElementUtilBase.inVirtualSpace(editor, offset)) {
      return false;
    }

    final PsiReference reference = TargetElementUtilBase.findReference(editor, offset);
    final Collection<PsiElement> candidates = suggestCandidates(reference);
    if (candidates.size() == 1) {
      PsiElement element = candidates.iterator().next();
      LOG.assertTrue(element != null);
      processor.execute(element);
      return true;
    }
    if (candidates.size() > 1) {
      PsiElement[] elements = candidates.toArray(new PsiElement[candidates.size()]);
      final TextRange range = reference.getRangeInElement();
      final String refText = range.substring(reference.getElement().getText());
      String title = MessageFormat.format(titlePattern, refText);
      NavigationUtil.getPsiElementPopup(elements, new DefaultPsiElementCellRenderer(), title, processor).showInBestPositionFor(editor);
      return true;
    }
    return false;
  }

  static Collection<PsiElement> suggestCandidates(final PsiReference reference) {
    if (reference == null) {
      return Collections.emptyList();
    }
    return TargetElementUtilBase.getInstance().getTargetCandidates(reference);
  }

  public boolean startInWriteAction() {
    return false;
  }

  @Nullable
  public static PsiElement findTargetElement(Project project, Editor editor, int offset) {
    if (TargetElementUtilBase.inVirtualSpace(editor, offset)) {
      return null;
    }

    return findTargetElementNoVS(project, editor, offset);
  }

  @Nullable
  public static PsiElement findTargetElementNoVS(Project project, Editor editor, int offset) {
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null) {
      return null;
    }
    PsiElement elementAt = file.findElementAt(offset);

    for (GotoDeclarationHandler handler : Extensions.getExtensions(GotoDeclarationHandler.EP_NAME)) {
      PsiElement result = handler.getGotoDeclarationTarget(elementAt);
      if (result != null) {
        return result;
      }
    }

    int flags = TargetElementUtilBase.getInstance().getAllAccepted() & ~TargetElementUtilBase.ELEMENT_NAME_ACCEPTED;
    PsiElement element = TargetElementUtilBase.getInstance().findTargetElement(editor, flags, offset);
    if (element != null) return element;

    // if no references found in injected fragment, try outer document
    if (editor instanceof EditorWindow) {
      EditorWindow window = (EditorWindow)editor;
      return findTargetElementNoVS(project, window.getDelegate(), window.getDocument().injectedToHost(offset));
    }
    return null;
  }
}
