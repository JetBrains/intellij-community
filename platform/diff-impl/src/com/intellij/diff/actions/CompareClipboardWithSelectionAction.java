/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.actions;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.actions.impl.MutableDiffRequestChain;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.Side;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CompareClipboardWithSelectionAction extends BaseShowDiffAction {
  @Nullable
  private static Editor getEditor(@NotNull AnActionEvent e) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor != null) return editor;

    Project project = e.getProject();
    if (project == null) return null;
    return FileEditorManager.getInstance(project).getSelectedTextEditor();
  }

  @Nullable
  private static FileType getEditorFileType(@NotNull AnActionEvent e) {
    DiffContent content = e.getData(DiffDataKeys.CURRENT_CONTENT);
    if (content != null && content.getContentType() != null) return content.getContentType();

    DiffRequest request = e.getData(DiffDataKeys.DIFF_REQUEST);
    if (request instanceof ContentDiffRequest) {
      for (DiffContent diffContent : ((ContentDiffRequest)request).getContents()) {
        FileType type = diffContent.getContentType();
        if (type != null && type != UnknownFileType.INSTANCE) return type;
      }
    }

    return null;
  }

  @Override
  protected boolean isAvailable(@NotNull AnActionEvent e) {
    Editor editor = getEditor(e);
    return editor != null;
  }

  @Nullable
  @Override
  protected DiffRequestChain getDiffRequestChain(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    Editor editor = getEditor(e);
    FileType editorFileType = getEditorFileType(e);
    assert editor != null;

    DiffContent selectedContent = e.getData(DiffDataKeys.CURRENT_CONTENT);
    DocumentContent content2 = createContent(project, editor, editorFileType, selectedContent);
    DocumentContent content1 = DiffContentFactory.getInstance().createClipboardContent(project, content2);
    content1.putUserData(BlankDiffWindowUtil.REMEMBER_CONTENT_KEY, true);

    MutableDiffRequestChain chain = BlankDiffWindowUtil.createBlankDiffRequestChain(content1, content2, null);
    chain.setWindowTitle(DiffBundle.message("diff.clipboard.vs.editor.dialog.title"));
    chain.setTitle1(DiffBundle.message("diff.content.clipboard.content.title"));
    chain.setTitle2(createContentTitle(editor));

    int currentLine = editor.getCaretModel().getLogicalPosition().line;
    chain.putRequestUserData(DiffUserDataKeys.SCROLL_TO_LINE, Pair.create(Side.RIGHT, currentLine));

    return chain;
  }

  @NotNull
  private static DocumentContent createContent(@Nullable Project project,
                                               @NotNull Editor editor,
                                               @Nullable FileType type,
                                               @Nullable DiffContent selectedContent) {
    DocumentContent content = null;
    if (selectedContent instanceof DocumentContent) {
      Document contentDocument = ((DocumentContent)selectedContent).getDocument();
      Document editorDocument = editor.getDocument();
      if (contentDocument.equals(editorDocument)) {
        content = (DocumentContent)selectedContent;
      }
    }

    if (content == null) {
      content = DiffContentFactory.getInstance().create(project, editor.getDocument(), type);
    }

    SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasSelection()) {
      TextRange range = new TextRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
      content = DiffContentFactory.getInstance().createFragment(project, content, range);
    }

    if (editor.isViewer()) content.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);

    return content;
  }

  @NotNull
  private static String createContentTitle(@NotNull Editor editor) {
    VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
    String title = file != null
                   ? DiffRequestFactory.getInstance().getContentTitle(file)
                   : DiffBundle.message("diff.content.editor.content.title");

    if (editor.getSelectionModel().hasSelection()) {
      title = DiffBundle.message("diff.content.selection.from.file.content.title", title);
    }

    return title;
  }
}
