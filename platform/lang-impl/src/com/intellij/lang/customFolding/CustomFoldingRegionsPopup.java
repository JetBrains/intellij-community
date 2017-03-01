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
package com.intellij.lang.customFolding;

import com.intellij.ide.IdeBundle;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Rustam Vishnyakov
 */
public class CustomFoldingRegionsPopup {
  public static void show(@NotNull final Collection<FoldingDescriptor> descriptors,
                          @NotNull final Editor editor,
                          @NotNull final Project project) {
    List<MyFoldingDescriptorWrapper> model =
      orderByPosition(descriptors)
        .stream()
        .map(descriptor -> new MyFoldingDescriptorWrapper(descriptor))
        .collect(Collectors.toList());
    JBPopupFactory.getInstance()
      .createPopupChooserBuilder(model)
      .setTitle(IdeBundle.message("goto.custom.region.command"))
      .setResizable(false)
      .setMovable(false)
      .setItemChoosenCallback((selection) -> {
        if (selection != null) {
          PsiElement navigationElement = selection.getDescriptor().getElement().getPsi();
          if (navigationElement != null) {
            navigateTo(editor, navigationElement);
            IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation();
          }
        }
      })
      .createPopup()
      .showInBestPositionFor(editor);
  }

  private static class MyFoldingDescriptorWrapper {
    private final @NotNull FoldingDescriptor myDescriptor;

    private MyFoldingDescriptorWrapper(@NotNull FoldingDescriptor descriptor) {
      myDescriptor = descriptor;
    }

    @NotNull
    public FoldingDescriptor getDescriptor() {
      return myDescriptor;
    }

    @Nullable
    @Override
    public String toString() {
      return myDescriptor.getPlaceholderText();
    }
  }

  private static List<FoldingDescriptor> orderByPosition(Collection<FoldingDescriptor> descriptors) {
    List<FoldingDescriptor> sorted = new ArrayList<>(descriptors.size());
    sorted.addAll(descriptors);
    Collections.sort(sorted, (descriptor1, descriptor2) -> {
      int pos1 = descriptor1.getElement().getTextRange().getStartOffset();
      int pos2 = descriptor2.getElement().getTextRange().getStartOffset();
      return pos1 - pos2;
    });
    return sorted;
  }

  private static void navigateTo(@NotNull Editor editor, @NotNull PsiElement element) {
    int offset = element.getTextRange().getStartOffset();
    if (offset >= 0 && offset < editor.getDocument().getTextLength()) {
      editor.getCaretModel().removeSecondaryCarets();
      editor.getCaretModel().moveToOffset(offset);
      editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
      editor.getSelectionModel().removeSelection();
    }
  }
}
