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
package com.intellij.ide.bookmarks;

import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.ui.popup.util.DetailView;
import com.intellij.ui.popup.util.DetailViewImpl;
import com.intellij.ui.popup.util.ItemWrapper;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.FileColorManager;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;
import java.awt.*;

/**
* Created with IntelliJ IDEA.
* User: zajac
* Date: 5/6/12
* Time: 2:06 AM
* To change this template use File | Settings | File Templates.
*/
public class BookmarkItem implements ItemWrapper {
  private final Bookmark myBookmark;

  public BookmarkItem(Bookmark bookmark) {
    myBookmark = bookmark;
  }

  public Bookmark getBookmark() {
    return myBookmark;
  }

  public void setupRenderer(ColoredListCellRenderer renderer, Project project, boolean selected) {
    VirtualFile file = myBookmark.getFile();

    PsiManager psiManager = PsiManager.getInstance(project);

    PsiElement fileOrDir = file.isDirectory() ? psiManager.findDirectory(file) : psiManager.findFile(file);
    if (fileOrDir != null) {
      renderer.setIcon(fileOrDir.getIcon(Iconable.ICON_FLAG_CLOSED));
    }


    FileStatus fileStatus = FileStatusManager.getInstance(project).getStatus(file);
    TextAttributes attributes = new TextAttributes(fileStatus.getColor(), null, null, EffectType.LINE_UNDERSCORE, Font.PLAIN);
    renderer.append(file.getName(), SimpleTextAttributes.fromTextAttributes(attributes));
    if (myBookmark.getLine() >= 0) {
      renderer.append(":", SimpleTextAttributes.GRAYED_ATTRIBUTES);
      renderer.append(String.valueOf(myBookmark.getLine() + 1), SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }

    if (!selected) {
      FileColorManager colorManager = FileColorManager.getInstance(project);
      if (fileOrDir instanceof PsiFile) {
        Color color = colorManager.getRendererBackground((PsiFile)fileOrDir);
        if (color != null) {
          renderer.setBackground(color);
        }
      }
    }

    String description = myBookmark.getDescription();
    if (description != null) {
      renderer.append(" " + description, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    }
  }

  public void updateMnemonicLabel(JLabel label) {
    final char mnemonic = myBookmark.getMnemonic();
    if (mnemonic != 0) {
      label.setText(Character.toString(mnemonic) + '.');
    }
    else {
      label.setText("");
    }
  }

  public String speedSearchText() {
    return myBookmark.getFile().getName() + " " + myBookmark.getDescription();
  }

  public void execute(Project project) {
    myBookmark.navigate();
  }

  public String footerText() {
    return myBookmark.getFile().getPresentableUrl();
  }

  @Override
  public void updateDetailView(final DetailView panel) {
    VirtualFile file = myBookmark.getFile();
    LogicalPosition positionToNavigate = new LogicalPosition(myBookmark.getLine(), 0);

    panel.navigateInPreviewEditor(file, positionToNavigate);
  }

  @Override
  public boolean allowedToRemove() {
    return true;
  }
}
