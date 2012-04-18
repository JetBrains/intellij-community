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
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
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
    final GotoData gotoData;
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      gotoData = new GotoData(source, new ImplementationSearcher.FirstImplementationsSearcher().searchImplementations(editor, source, offset),
                              Collections.<AdditionalAction>emptyList());
      gotoData.listUpdaterTask = new ImplementationsUpdaterTask(gotoData, editor, offset);
    } else {
      gotoData = new GotoData(source, new ImplementationSearcher().searchImplementations(editor, source, offset),
                              Collections.<AdditionalAction>emptyList());
    }
    return gotoData;
  }

  protected String getChooserTitle(PsiElement sourceElement, String name, int length) {
    return CodeInsightBundle.message("goto.implementation.chooserTitle", name, length);
  }

  @Override
  protected String getNotFoundMessage(Project project, Editor editor, PsiFile file) {
    return CodeInsightBundle.message("goto.implementation.notFound");
  }

  private class ImplementationsUpdaterTask extends ListBackgroundUpdaterTask {
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
    public void run(final @NotNull ProgressIndicator indicator) {
      super.run(indicator);
      for (PsiElement element : myGotoData.targets) {
        if (!updateComponent(element, createComparator(renderers, myGotoData))) {
          return;
        }
      }
      new ImplementationSearcher.BackgroundableImplementationSearcher() {
        protected void processElement(PsiElement element) {
          if (myGotoData.addTarget(element)) {
            if (!updateComponent(element, createComparator(renderers, myGotoData))) {
              indicator.cancel();
            }
          }
          indicator.checkCanceled();
        }
      }.searchImplementations(myEditor, myGotoData.source, myOffset);
    }

    @Override
    public String getCaption(int size) {
      return getChooserTitle(myGotoData.source, ((PsiNamedElement)myGotoData.source).getName(), size);
    }
  }
}
