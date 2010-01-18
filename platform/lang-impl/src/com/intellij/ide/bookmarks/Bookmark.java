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

package com.intellij.ide.bookmarks;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.LightColors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class Bookmark {
  private static final Icon TICK = IconLoader.getIcon("/gutter/check.png");

  private final VirtualFile myFile;
  private final OpenFileDescriptor myTarget;
  private final RangeHighlighter myHighlighter;
  private final Project myProject;

  private String myDescription;
  private char myMnemonic = 0;
  public static final Font MNEMONIC_FONT = new Font("Monospaced", 0, 11);

  public Bookmark(Project project, VirtualFile file, String description) {
    this(project, file, -1, description);
  }

  public Bookmark(Project project, VirtualFile file, int line, String description) {
    myFile = file;
    myProject = project;
    myDescription = description;

    if (line >= 0) {
      MarkupModelEx markup = (MarkupModelEx)getDocument().getMarkupModel(myProject);
      myHighlighter = markup.addPersistentLineHighlighter(line, HighlighterLayer.ERROR + 1, null);


      if (myHighlighter != null) {
        myHighlighter.setGutterIconRenderer(new GutterIconRenderer() {
          @NotNull
          public Icon getIcon() {
            return Bookmark.this.getIcon();
          }

          public String getTooltipText() {
            return StringUtil.escapeXml(getNotEmptyDescription());
          }
        });

        myHighlighter.setErrorStripeMarkColor(Color.black);
        myHighlighter.setErrorStripeTooltip(StringUtil.escapeXml(getNotEmptyDescription()));
      }
    }
    else {
      myHighlighter = null;
    }

    myTarget = new OpenFileDescriptor(project, file, line, -1);
  }

  public Document getDocument() {
    return FileDocumentManager.getInstance().getDocument(getFile());
  }

  public void release() {
    if (myHighlighter != null) {
      getDocument().getMarkupModel(myProject).removeHighlighter(myHighlighter);
    }
  }

  public Icon getIcon() {
    return myMnemonic == 0 ? TICK : new MnemonicIcon(myMnemonic);
  }

  public String getDescription() {
    return myDescription;
  }

  public void setDescription(String description) {
    myDescription = description;
  }

  public char getMnemonic() {
    return myMnemonic;
  }

  public void setMnemonic(char mnemonic) {
    myMnemonic = Character.toUpperCase(mnemonic);
  }

  public VirtualFile getFile() {
    return myFile;
  }

  @Nullable
  public String getNotEmptyDescription() {
    return isDescriptionEmpty() ? null : myDescription;
  }

  public boolean isDescriptionEmpty() {
    return myDescription == null || myDescription.trim().length() == 0;
  }

  public boolean isValid() {
    return getFile().isValid() && (myHighlighter == null || myHighlighter.isValid());
  }

  public void navigate() {
    myTarget.navigate(true);
  }

  public int getLine() {
    if (myHighlighter != null && myHighlighter.isValid()) {
      return myHighlighter.getDocument().getLineNumber(myHighlighter.getStartOffset());
    }
    return myTarget.getLine();
  }

  @Override
  public String toString() {
    return getQualifiedName();
  }

  public String getQualifiedName() {
    String presentableUrl = myFile.getPresentableUrl();
    if (myFile.isDirectory() || myHighlighter == null) return presentableUrl;

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(myFile);

    if (psiFile == null) return presentableUrl;

    StructureViewBuilder builder = LanguageStructureViewBuilder.INSTANCE.getStructureViewBuilder(psiFile);
    if (builder instanceof TreeBasedStructureViewBuilder) {
      StructureViewModel model = ((TreeBasedStructureViewBuilder)builder).createStructureViewModel();
      Object element = model.getCurrentEditorElement();
      if (element instanceof NavigationItem) {
        ItemPresentation presentation = ((NavigationItem)element).getPresentation();
        if (presentation != null) {
          presentableUrl = ((NavigationItem)element).getName() + " " + presentation.getLocationString();
        }
      }
    }

    return IdeBundle
      .message("bookmark.file.X.line.Y", presentableUrl, (myHighlighter.getDocument().getLineNumber(myHighlighter.getStartOffset()) + 1));
  }

  private static class MnemonicIcon implements Icon {
    private final char myMnemonic;

    private MnemonicIcon(char mnemonic) {
      myMnemonic = mnemonic;
    }

    public void paintIcon(Component c, Graphics g, int x, int y) {
      g.setColor(LightColors.YELLOW);
      g.fillRect(x, y, getIconWidth(), getIconHeight());

      g.setColor(Color.gray);
      g.drawRect(x, y, getIconWidth(), getIconHeight());

      g.setColor(Color.black);
      g.setFont(MNEMONIC_FONT);

      g.drawString(Character.toString(myMnemonic), x + 2, y + getIconHeight() - 2);
    }

    public int getIconWidth() {
      return 10;
    }

    public int getIconHeight() {
      return 12;
    }
  }
}
