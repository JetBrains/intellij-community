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
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author Rustam Vishnyakov
 */
public class CustomFoldingRegionsPopup {
  private final @NotNull JBList myRegionsList;
  private final @NotNull JBPopup myPopup;
  private final @NotNull Editor myEditor;

  CustomFoldingRegionsPopup(@NotNull Collection<FoldingDescriptor> descriptors,
                            @NotNull final Editor editor,
                            @NotNull final Project project) {
    myEditor = editor;
    myRegionsList = new JBList();
    //noinspection unchecked
    myRegionsList.setModel(new MyListModel(orderByPosition(descriptors)));
    myRegionsList.setSelectedIndex(0);

    final PopupChooserBuilder popupBuilder = JBPopupFactory.getInstance().createListPopupBuilder(myRegionsList);
      myPopup = popupBuilder
        .setTitle(IdeBundle.message("goto.custom.region.command"))
        .setResizable(false)
        .setMovable(false)
        .setItemChoosenCallback(() -> {
          PsiElement navigationElement = getNavigationElement();
          if (navigationElement != null) {
            navigateTo(editor, navigationElement);
            IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation();
          }
        }).createPopup();
  }

  void show() {
    myPopup.showInBestPositionFor(myEditor);
  }

  private static class MyListModel extends DefaultListModel {
    private MyListModel(Collection<FoldingDescriptor> descriptors) {
      for (FoldingDescriptor descriptor : descriptors) {
        //noinspection unchecked
        super.addElement(new MyFoldingDescriptorWrapper(descriptor));
      }
    }
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

  @Nullable
  public PsiElement getNavigationElement() {
    Object selection = myRegionsList.getSelectedValue();
    if (selection instanceof MyFoldingDescriptorWrapper) {
      return  ((MyFoldingDescriptorWrapper)selection).getDescriptor().getElement().getPsi();
    }
    return null;
  }

  private static Collection<FoldingDescriptor> orderByPosition(Collection<FoldingDescriptor> descriptors) {
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
