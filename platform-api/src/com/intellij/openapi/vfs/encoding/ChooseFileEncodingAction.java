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
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;

public abstract class ChooseFileEncodingAction extends ComboBoxAction {
  private final VirtualFile myVirtualFile;
  private final Project myProject;

  public ChooseFileEncodingAction(VirtualFile virtualFile, Project project) {
    myVirtualFile = virtualFile;
    myProject = project;
  }

  public void update(final AnActionEvent e) {
    boolean enabled = isEnabled(myProject, myVirtualFile);
    if (myVirtualFile != null) {
      String prefix;
      Charset charset = encodingFromContent(myProject, myVirtualFile);
      if (charset != null) {
        prefix = "Encoding:";
      }
      else {
        prefix = "";
      }
      if (charset == null) charset = myVirtualFile.getCharset();
      e.getPresentation().setText(prefix + " " + charset.toString());
    }
    e.getPresentation().setEnabled(enabled);
  }

  public static boolean isEnabled(Project project, VirtualFile virtualFile) {
    if (project == null) return false;
    boolean enabled = true;
    if (virtualFile != null) {
      Charset charset = encodingFromContent(project, virtualFile);
      if (charset != null) {
        enabled = false;
      }
      else if (!virtualFile.isDirectory()) {
        FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(virtualFile);
        if (fileType.isBinary()
            || fileType == StdFileTypes.GUI_DESIGNER_FORM
            || fileType == StdFileTypes.IDEA_MODULE
            || fileType == StdFileTypes.IDEA_PROJECT
            || fileType == StdFileTypes.IDEA_WORKSPACE
            || fileType == StdFileTypes.PATCH
          ) {
          enabled = false;
        }
      }
    }
    return enabled;
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
    return createGroup(true);
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
      chosen(myFile, NO_ENCODING);
    }
  }

  public static final Charset NO_ENCODING = new Charset("NO_ENCODING", null) {
    public boolean contains(final Charset cs) {
      return false;
    }

    public CharsetDecoder newDecoder() {
      return null;
    }

    public CharsetEncoder newEncoder() {
      return null;
    }
  };
  protected abstract void chosen(VirtualFile virtualFile, Charset charset);

  public DefaultActionGroup createGroup(boolean showClear) {
    DefaultActionGroup group = new DefaultActionGroup();
    List<Charset> favorites = new ArrayList<Charset>(EncodingManager.getInstance().getFavorites());
    Collections.sort(favorites);

    if (showClear) {
      group.add(new ClearThisFileEncodingAction(myVirtualFile));
    }
    fillCharsetActions(group, myVirtualFile, favorites);

    DefaultActionGroup more = new DefaultActionGroup("more", true);
    group.add(more);
    fillCharsetActions(more, myVirtualFile, Arrays.asList(CharsetToolkit.getAvailableCharsets()));
    return group;
  }
}
