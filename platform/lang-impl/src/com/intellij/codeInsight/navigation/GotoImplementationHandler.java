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
import com.intellij.codeInsight.ContainerProvider;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
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

import java.util.Collections;
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
    final PsiReference reference = TargetElementUtil.findReference(editor, offset);
    final TargetElementUtil instance = TargetElementUtil.getInstance();
    PsiElement[] targets = new ImplementationSearcher.FirstImplementationsSearcher() {
      @Override
      protected boolean accept(PsiElement element) {
        return instance.acceptImplementationForReference(reference, element);
      }

      @Override
      protected boolean canShowPopupWithOneItem(PsiElement element) {
        return false;
      }
    }.searchImplementations(editor, source, offset);
    if (targets == null) return null;
    GotoData gotoData = new GotoData(source, targets, Collections.emptyList());
    gotoData.listUpdaterTask = new ImplementationsUpdaterTask(gotoData, editor, offset, reference);
    return gotoData;
  }


  private static PsiElement getContainer(PsiElement refElement) {
    for (ContainerProvider provider : ContainerProvider.EP_NAME.getExtensions()) {
      final PsiElement container = provider.getContainer(refElement);
      if (container != null) return container;
    }
    return refElement.getParent();
  }

  @Override
  @NotNull
  protected String getChooserTitle(@NotNull PsiElement sourceElement, String name, int length, boolean finished) {
    ItemPresentation presentation = ((NavigationItem)sourceElement).getPresentation();
    String fullName;
    if (presentation == null) {
      fullName = name;
    }
    else {
      PsiElement container = getContainer(sourceElement);
      ItemPresentation containerPresentation = container == null || container instanceof PsiFile ? null : ((NavigationItem)container).getPresentation();
      String containerText = containerPresentation == null ? null : containerPresentation.getPresentableText();
      fullName = (containerText == null ? "" : containerText+".") + presentation.getPresentableText();
    }
    return CodeInsightBundle.message("goto.implementation.chooserTitle", fullName, length, finished ? "" : " so far");
  }

  @NotNull
  @Override
  protected String getFindUsagesTitle(@NotNull PsiElement sourceElement, String name, int length) {
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
    private final Map<Object, PsiElementListCellRenderer> renderers = new HashMap<>();
    private final PsiReference myReference;

    ImplementationsUpdaterTask(@NotNull GotoData gotoData, @NotNull Editor editor, int offset, final PsiReference reference) {
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
      return getChooserTitle(myGotoData.source, ((PsiNamedElement)myGotoData.source).getName(), size, isFinished());
    }
  }
}
