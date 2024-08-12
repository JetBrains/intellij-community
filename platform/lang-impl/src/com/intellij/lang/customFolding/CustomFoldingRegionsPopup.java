// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.customFolding;

import com.intellij.ide.IdeBundle;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class CustomFoldingRegionsPopup {
  public static void show(final @NotNull Collection<? extends FoldingDescriptor> descriptors,
                          final @NotNull Editor editor,
                          final @NotNull Project project) {
    List<MyFoldingDescriptorWrapper> model = orderByPosition(descriptors);
    JBPopupFactory.getInstance()
      .createPopupChooserBuilder(model)
      .setTitle(IdeBundle.message("goto.custom.region.command"))
      .setResizable(false)
      .setMovable(false)
      .setItemChosenCallback((selection) -> {
        PsiElement navigationElement = selection.getDescriptor().getElement().getPsi();
        if (navigationElement != null) {
          navigateTo(editor, navigationElement);
          IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation();
        }
      })
      .createPopup()
      .showInBestPositionFor(editor);
  }

  private static final class MyFoldingDescriptorWrapper {
    private final @NotNull FoldingDescriptor myDescriptor;
    private final int myIndent;

    private MyFoldingDescriptorWrapper(@NotNull FoldingDescriptor descriptor, int indent) {
      myDescriptor = descriptor;
      myIndent = indent;
    }

    public @NotNull FoldingDescriptor getDescriptor() {
      return myDescriptor;
    }

    @Override
    public String toString() {
      return StringUtil.repeat("   ", myIndent) + myDescriptor.getPlaceholderText();
    }
  }

  private static List<MyFoldingDescriptorWrapper> orderByPosition(Collection<? extends FoldingDescriptor> descriptors) {
    List<FoldingDescriptor> sorted = new ArrayList<>(descriptors.size());
    sorted.addAll(descriptors);
    sorted.sort((descriptor1, descriptor2) -> {
      int pos1 = descriptor1.getElement().getTextRange().getStartOffset();
      int pos2 = descriptor2.getElement().getTextRange().getStartOffset();
      return pos1 - pos2;
    });
    Stack<FoldingDescriptor> stack = new Stack<>();
    List<MyFoldingDescriptorWrapper> result = new ArrayList<>();
    for (FoldingDescriptor descriptor : sorted) {
      while (!stack.isEmpty() && descriptor.getRange().getStartOffset() >= stack.peek().getRange().getEndOffset()) stack.pop();
      result.add(new MyFoldingDescriptorWrapper(descriptor, stack.size()));
      stack.push(descriptor);
    }
    return result;
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
