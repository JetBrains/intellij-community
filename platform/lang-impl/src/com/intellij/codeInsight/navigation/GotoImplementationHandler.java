/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class GotoImplementationHandler extends GotoTargetHandler {
  @Override
  protected String getFeatureUsedKey() {
    return "navigation.goto.implementation";
  }

  @Override
  @Nullable
  public GotoData getSourceAndTargetElements(@NotNull Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement source = TargetElementUtil.getInstance().findTargetElement(editor, ImplementationSearcher.getFlags(), offset);
    if (source == null) return null;
    final GotoData gotoData;
    final PsiReference reference = TargetElementUtil.findReference(editor, offset);
    final TargetElementUtil instance = TargetElementUtil.getInstance();
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      gotoData = new GotoData(source, new ImplementationSearcher.FirstImplementationsSearcher(){
        @Override
        protected boolean accept(PsiElement element) {
          return instance.acceptImplementationForReference(reference, element);
        }

        @Override
        protected boolean canShowPopupWithOneItem(PsiElement element) {
          return false;
        }
      }.searchImplementations(editor, source, offset), Collections.<AdditionalAction>emptyList());

      gotoData.listUpdaterTask = new ImplementationsUpdaterTask(gotoData, editor, offset, reference);
    } else {
      gotoData = new GotoData(source, new ImplementationSearcher(){
        @Override
        protected PsiElement[] filterElements(PsiElement element, PsiElement[] targetElements, int offset) {
          final List<PsiElement> result = new ArrayList<PsiElement>();
          for (PsiElement targetElement : targetElements) {
            if (instance.acceptImplementationForReference(reference, targetElement)) {
              result.add(targetElement);
            }
          }
          return result.toArray(new PsiElement[result.size()]);
        }
      }.searchImplementations(editor, source, offset),
                              Collections.<AdditionalAction>emptyList());
    }
    return gotoData;
  }

  @NotNull
  @Override
  protected String getChooserTitle(PsiElement sourceElement, String name, int length) {
    return CodeInsightBundle.message("goto.implementation.chooserTitle", name, length);
  }

  @NotNull
  @Override
  protected String getFindUsagesTitle(PsiElement sourceElement, String name, int length) {
    return CodeInsightBundle.message("goto.implementation.findUsages.title", name, length);
  }

  @NotNull
  @Override
  protected String getNotFoundMessage(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return CodeInsightBundle.message("goto.implementation.notFound");
  }

  private class ImplementationsUpdaterTask extends ListBackgroundUpdaterTask {
    private final Editor myEditor;
    private final int myOffset;
    private final GotoData myGotoData;
    private final Map<Object, PsiElementListCellRenderer> renderers = new HashMap<Object, PsiElementListCellRenderer>();
    private final PsiReference myReference;

    public ImplementationsUpdaterTask(@NotNull GotoData gotoData, @NotNull Editor editor, int offset, final PsiReference reference) {
      super(gotoData.source.getProject(), ImplementationSearcher.SEARCHING_FOR_IMPLEMENTATIONS);
      myEditor = editor;
      myOffset = offset;
      myGotoData = gotoData;
      myReference = reference;
    }

    @Override
    public void run(@NotNull final ProgressIndicator indicator) {
      super.run(indicator);
      for (PsiElement element : myGotoData.targets) {
        if (!updateComponent(element, createComparator(renderers, myGotoData))) {
          return;
        }
      }
      new ImplementationSearcher.BackgroundableImplementationSearcher() {
        @Override
        protected void processElement(PsiElement element) {
          indicator.checkCanceled();
          if (!TargetElementUtil.getInstance().acceptImplementationForReference(myReference, element)) return;
          if (myGotoData.addTarget(element)) {
            if (!updateComponent(element, createComparator(renderers, myGotoData))) {
              indicator.cancel();
            }
          }
        }
      }.searchImplementations(myEditor, myGotoData.source, myOffset);
    }

    @Override
    public String getCaption(int size) {
      return getChooserTitle(myGotoData.source, ((PsiNamedElement)myGotoData.source).getName(), size);
    }
  }
}
