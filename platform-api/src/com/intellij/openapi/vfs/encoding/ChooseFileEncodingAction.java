/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jul 19, 2007
 * Time: 5:53:46 PM
 */
package com.intellij.openapi.vfs.encoding;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ChooseFileEncodingAction extends ComboBoxAction {
  private final VirtualFile myVirtualFile;
  private final Project myProject;

  public ChooseFileEncodingAction(VirtualFile virtualFile, Project project) {
    myVirtualFile = virtualFile;
    myProject = project;
  }

  public void update(final AnActionEvent e) {
    boolean enabled = true;
    if (myVirtualFile != null) {
      String prefix;
      Charset charset = encodingFromContent(myProject, myVirtualFile);
      if (charset != null) {
        prefix = "Encoding:";
        enabled = false;
      }
      else {
        prefix = "";
      }
      if (charset == null) charset = myVirtualFile.getCharset();
      e.getPresentation().setText(prefix + " " + charset.toString());
    }
    e.getPresentation().setEnabled(enabled);
  }

  public static Charset encodingFromContent(Project project, VirtualFile virtualFile) {
    FileType fileType = virtualFile.getFileType();
    if (fileType instanceof LanguageFileType) {
      Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
      if (document == null) return null;
      String text = document.getText();
      return ((LanguageFileType)fileType).extractCharsetFromFileContent(project, virtualFile, text);
    }
    return null;
  }

  @NotNull
  protected DefaultActionGroup createPopupActionGroup(final JComponent button) {
    DefaultActionGroup group = new DefaultActionGroup();
    List<Charset> favorites = new ArrayList<Charset>(EncodingManager.getInstance().getFavorites());
    Collections.sort(favorites);

    group.add(new ClearThisFileEncodingAction(myVirtualFile));
    fillCharsetActions(group, myVirtualFile, favorites);

    DefaultActionGroup more = new DefaultActionGroup("more", true);
    group.add(more);
    fillCharsetActions(more, myVirtualFile, Arrays.asList(CharsetToolkit.getAvailableCharsets()));
    return group;
  }

  private void fillCharsetActions(DefaultActionGroup group, final VirtualFile virtualFile, List<Charset> charsets) {
    for (Charset slave : charsets) {
      ChangeFileEncodingTo action = new ChangeFileEncodingTo(virtualFile, slave){
        protected void chosen(final VirtualFile file, final Charset charset) {
          ChooseFileEncodingAction.this.chosen(file, charset);
        }
      };
      group.add(action);
    }
  }

  private class ClearThisFileEncodingAction extends AnAction {
    private final VirtualFile myFile;

    private ClearThisFileEncodingAction(@Nullable VirtualFile file) {
      super("<Clear>", "Clear " +
                       (file == null ? "default" : "file '"+file.getName()+"'") +
                       " encoding.", null);
      myFile = file;
    }

    public void actionPerformed(final AnActionEvent e) {
      chosen(myFile, null);
    }
  }

  protected void chosen(VirtualFile virtualFile, Charset charset) {
    EncodingManager.getInstance().setEncoding(virtualFile, charset);
  }
}
