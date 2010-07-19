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

package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.lang.Language;
import com.intellij.lang.parameterInfo.LanguageParameterInfo;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LightweightHint;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Set;

public class ShowParameterInfoHandler implements CodeInsightActionHandler {
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    invoke(project, editor, file, -1, null);
  }

  public boolean startInWriteAction() {
    return false;
  }

  public void invoke(final Project project, final Editor editor, PsiFile file, int lbraceOffset,
                     PsiElement highlightedElement) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final int offset = editor.getCaretModel().getOffset();
    final PsiElement psiElement = file.findElementAt(offset);
    if (psiElement == null) return;

    final ShowParameterInfoContext context = new ShowParameterInfoContext(
      editor,
      project,
      file,
      offset,
      lbraceOffset
    );

    context.setHighlightedElement(highlightedElement);

    final Language language = psiElement.getLanguage();
    ParameterInfoHandler[] handlers = getHandlers(project, language, file.getViewProvider().getBaseLanguage());
    if (handlers == null) handlers = new ParameterInfoHandler[0];

    Lookup lookup = LookupManager.getInstance(project).getActiveLookup();

    if (lookup != null) {
      LookupElement item = lookup.getCurrentItem();

      if (item != null) {
        for(ParameterInfoHandler handler:handlers) {
          if (handler.couldShowInLookup()) {
            final Object[] items = handler.getParametersForLookup(item, context);
            if (items != null && items.length > 0) showLookupEditorHint(items, editor, project,handler);
            return;
          }
        }
      }
      return;
    }

    for(ParameterInfoHandler handler:handlers) {
      Object element = handler.findElementForParameterInfo(context);
      if (element != null) {
        handler.showParameterInfo(element,context);
      }
    }
  }

  private static void showLookupEditorHint(Object[] descriptors, final Editor editor, final Project project, ParameterInfoHandler handler) {
    ParameterInfoComponent component = new ParameterInfoComponent(descriptors, editor, handler);
    component.update();

    final LightweightHint hint = new LightweightHint(component);
    hint.setSelectingHint(true);
    final HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();
    final Point p = ShowParameterInfoContext.chooseBestHintPosition(project, editor, -1, -1, hint);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (!editor.getComponent().isShowing()) return;
        hintManager.showEditorHint(hint, editor, p,
                                   HintManagerImpl.HIDE_BY_ANY_KEY | HintManagerImpl.HIDE_BY_LOOKUP_ITEM_CHANGE | HintManagerImpl.UPDATE_BY_SCROLLING,
                                   0, false);
      }
    });
  }

  @Nullable
  public static ParameterInfoHandler[] getHandlers(Project project, final Language... languages) {
    Set<ParameterInfoHandler> handlers = new THashSet<ParameterInfoHandler>();
    for (final Language language : languages) {
      handlers.addAll(DumbService.getInstance(project).filterByDumbAwareness(LanguageParameterInfo.INSTANCE.allForLanguage(language)));
    }
    if (handlers.isEmpty()) return null;
    return handlers.toArray(new ParameterInfoHandler[handlers.size()]);
  }

  interface BestLocationPointProvider {
    @NotNull
    Point getBestPointPosition(LightweightHint hint, final PsiElement list, int offset);
  }

}

