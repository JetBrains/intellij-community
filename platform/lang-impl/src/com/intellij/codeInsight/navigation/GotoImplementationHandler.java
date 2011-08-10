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

package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.daemon.impl.AppenderTask;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.searches.DefinitionsSearch;
import com.intellij.util.CommonProcessors;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

public class GotoImplementationHandler extends GotoTargetHandler {
  protected String getFeatureUsedKey() {
    return "navigation.goto.implementation";
  }

  @Nullable
  public GotoData getSourceAndTargetElements(Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement source = TargetElementUtilBase.getInstance().findTargetElement(editor, ImplementationSearcher.getFlags(), offset);
    if (source == null) return null;
    final GotoData gotoData = new GotoData(source, new ImplementationSearcher.FirstImplementationsSearcher().searchImplementations(editor, source, offset),
                                           Collections.<AdditionalAction>emptyList());
    gotoData.appenderTask = new ImplementationsUpdaterTask(gotoData, editor, offset);
    return gotoData;
  }

  protected String getChooserTitle(PsiElement sourceElement, String name, int length) {
    return CodeInsightBundle.message("goto.implementation.chooserTitle", name, length);
  }

  @Override
  protected String getNotFoundMessage(Project project, Editor editor, PsiFile file) {
    return CodeInsightBundle.message("goto.implementation.notFound");
  }

  private class ImplementationsUpdaterTask extends AppenderTask {
    private Editor myEditor;
    private int myOffset;
    private GotoData myGotoData;
    private final Map<Object, PsiElementListCellRenderer> renderers = new HashMap<Object, PsiElementListCellRenderer>();

    public ImplementationsUpdaterTask(GotoData gotoData, Editor editor, int offset) {
      super(gotoData.source.getProject(), ImplementationSearcher.SEARCHING_FOR_IMPLEMENTATIONS);
      myEditor = editor;
      myOffset = offset;
      myGotoData = gotoData;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      super.run(indicator);
      new ImplementationSearcher() {
        @Override
        protected PsiElement[] searchDefinitions(final PsiElement element) {
          final CommonProcessors.CollectProcessor<PsiElement> processor = new CommonProcessors.CollectProcessor<PsiElement>() {
            @Override
            public boolean process(PsiElement element) {
              myGotoData.addTarget(element);
              updateList(element, createComparator(renderers, myGotoData));
              return super.process(element);
            }
          };
          try {
            DefinitionsSearch.search(element).forEach(processor);
          }
          catch (IndexNotReadyException e) {
            ImplementationSearcher.dumbModeNotification(element);
            return null;
          }
          return processor.toArray(PsiElement.EMPTY_ARRAY);
        }
      }.searchImplementations(myEditor, myGotoData.source, myOffset);
    }

    @Override
    public String getCaption(int size) {
      return getChooserTitle(myGotoData.source, ((PsiNamedElement)myGotoData.source).getName(), size);
    }
  }
}
