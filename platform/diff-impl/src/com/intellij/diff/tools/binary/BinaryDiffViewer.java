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
package com.intellij.diff.tools.binary;

import com.intellij.diff.DiffContext;
import com.intellij.diff.actions.impl.FocusOppositePaneAction;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.contents.EmptyContent;
import com.intellij.diff.contents.FileContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.util.DiffNotifications;
import com.intellij.diff.tools.util.FocusTrackerSupport;
import com.intellij.diff.tools.util.SimpleDiffPanel;
import com.intellij.diff.tools.util.StatusPanel;
import com.intellij.diff.tools.util.base.ListenerDiffViewerBase;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileTypes.UIBasedFileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class BinaryDiffViewer extends ListenerDiffViewerBase {
  public static final Logger LOG = Logger.getInstance(BinaryDiffViewer.class);

  @NotNull private final SimpleDiffPanel myPanel;
  @NotNull private final BinaryContentPanel myContentPanel;
  @NotNull private final MyStatusPanel myStatusPanel;

  @Nullable private final FileEditor myEditor1;
  @Nullable private final FileEditor myEditor2;
  @Nullable private final FileEditorProvider myEditorProvider1;
  @Nullable private final FileEditorProvider myEditorProvider2;

  @NotNull private final FocusTrackerSupport.TwosideFocusTrackerSupport myFocusTrackerSupport;

  public BinaryDiffViewer(@NotNull DiffContext context, @NotNull DiffRequest request) {
    super(context, (ContentDiffRequest)request);

    List<JComponent> titlePanel = DiffUtil.createSimpleTitles(myRequest);
    Couple<Pair<FileEditor, FileEditorProvider>> editors = createEditors();

    myEditor1 = editors.first.first;
    myEditorProvider1 = editors.first.second;
    myEditor2 = editors.second.first;
    myEditorProvider2 = editors.second.second;
    assert myEditor1 != null || myEditor2 != null;

    myFocusTrackerSupport = new FocusTrackerSupport.TwosideFocusTrackerSupport(getEditor1(), getEditor2());
    myContentPanel = new BinaryContentPanel(titlePanel, getEditor1(), getEditor2());

    myPanel = new SimpleDiffPanel(myContentPanel, this, context);

    myStatusPanel = new MyStatusPanel();

    new MyFocusOppositePaneAction().setupAction(myPanel);
  }

  @CalledInAwt
  public void onDispose() {
    destroyEditors();
    super.onDispose();
  }

  @Override
  @CalledInAwt
  protected void processContextHints() {
    super.processContextHints();
    myFocusTrackerSupport.processContextHints(myRequest, myContext);
  }

  @Override
  @CalledInAwt
  protected void updateContextHints() {
    super.updateContextHints();
    myFocusTrackerSupport.updateContextHints(myRequest, myContext);
  }

  //
  // Editors
  //

  @NotNull
  protected Couple<Pair<FileEditor, FileEditorProvider>> createEditors() {
    List<DiffContent> contents = myRequest.getContents();

    Pair<FileEditor, FileEditorProvider> pair1;
    Pair<FileEditor, FileEditorProvider> pair2;

    try {
      pair1 = createEditor(contents.get(0));
      pair2 = createEditor(contents.get(1));
      return Couple.of(pair1, pair2);
    }
    catch (IOException e) {
      LOG.error(e);
      Pair<FileEditor, FileEditorProvider> empty = Pair.empty();
      return Couple.of(empty, empty);
    }
  }

  @NotNull
  private Pair<FileEditor, FileEditorProvider> createEditor(@NotNull final DiffContent content) throws IOException {
    if (content instanceof EmptyContent) return Pair.empty();
    if (content instanceof FileContent) {
      Project project = myProject != null ? myProject : ProjectManager.getInstance().getDefaultProject();
      VirtualFile file = ((FileContent)content).getFile();

      FileEditorProvider[] providers = FileEditorProviderManager.getInstance().getProviders(project, file);
      if (providers.length == 0) throw new IOException("Can't find FileEditorProvider");

      FileEditorProvider provider = providers[0];
      FileEditor editor = provider.createEditor(project, file);

      UIUtil.removeScrollBorder(editor.getComponent());

      return Pair.create(editor, provider);
    }
    if (content instanceof DocumentContent) {
      Document document = ((DocumentContent)content).getDocument();
      final Editor editor = DiffUtil.createEditor(document, myProject, true);

      TextEditorProvider provider = TextEditorProvider.getInstance();
      TextEditor fileEditor = provider.getTextEditor(editor);

      Disposer.register(fileEditor, new Disposable() {
        @Override
        public void dispose() {
          EditorFactory.getInstance().releaseEditor(editor);
        }
      });

      return Pair.<FileEditor, FileEditorProvider>create(fileEditor, provider);
    }
    throw new IllegalArgumentException(content.getClass() + " - " + content.toString());
  }


  private void destroyEditors() {
    if (getEditor1() != null) {
      assert myEditorProvider1 != null;
      myEditorProvider1.disposeEditor(getEditor1());
    }
    if (getEditor2() != null) {
      assert myEditorProvider2 != null;
      myEditorProvider2.disposeEditor(getEditor2());
    }
  }

  //
  // Diff
  //

  @Override
  protected void onSlowRediff() {
    super.onSlowRediff();
    myStatusPanel.setBusy(true);
  }

  @Override
  @NotNull
  protected Runnable performRediff(@NotNull final ProgressIndicator indicator) {
    try {
      indicator.checkCanceled();

      List<DiffContent> contents = myRequest.getContents();

      if (contents.get(0) instanceof EmptyContent) {
        return applyNotification(DiffNotifications.INSERTED_CONTENT);
      }

      if (contents.get(1) instanceof EmptyContent) {
        return applyNotification(DiffNotifications.REMOVED_CONTENT);
      }

      if (!(contents.get(0) instanceof FileContent) || !(contents.get(1) instanceof FileContent)) {
        return applyNotification(null);
      }

      final VirtualFile file1 = ((FileContent)contents.get(0)).getFile();
      final VirtualFile file2 = ((FileContent)contents.get(1)).getFile();
      if (!file1.isValid() || !file2.isValid()) {
        return applyNotification(DiffNotifications.ERROR);
      }

      final boolean equal = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          try {
            // we can't use getInputStream() here because we can't restore BOM marker
            // (getBom() can return null for binary files, while getInputStream() strips BOM for all files).
            // It can be made for files from VFS that implements FileSystemInterface though.
            byte[] bytes1 = file1.contentsToByteArray();
            byte[] bytes2 = file2.contentsToByteArray();
            return Arrays.equals(bytes1, bytes2);
          }
          catch (IOException e) {
            LOG.warn(e);
            return false;
          }
        }
      });

      return applyNotification(equal ? DiffNotifications.EQUAL_CONTENTS : null);
    }
    catch (ProcessCanceledException ignore) {
      return applyNotification(DiffNotifications.OPERATION_CANCELED);
    }
    catch (Throwable e) {
      LOG.error(e);
      return applyNotification(DiffNotifications.ERROR);
    }
  }

  @NotNull
  private Runnable applyNotification(@Nullable final JComponent notification) {
    return new Runnable() {
      @Override
      public void run() {
        clearDiffPresentation();
        if (notification != null) myPanel.addNotification(notification);
      }
    };
  }

  private void clearDiffPresentation() {
    myStatusPanel.setBusy(false);
    myPanel.resetNotifications();
  }

  //
  // Getters
  //

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return getCurrentEditor().getPreferredFocusedComponent();
  }

  @NotNull
  public Side getCurrentSide() {
    return myFocusTrackerSupport.getCurrentSide();
  }

  public void setCurrentSide(@NotNull Side side) {
    myFocusTrackerSupport.setCurrentSide(side);
  }

  @Nullable
  FileEditor getEditor2() {
    return myEditor2;
  }

  @Nullable
  FileEditor getEditor1() {
    return myEditor1;
  }

  @NotNull
  FileEditor getCurrentEditor() {
    //noinspection ConstantConditions
    return getCurrentSide().select(getEditor1(), getEditor2());
  }

  @NotNull
  @Override
  protected JComponent getStatusPanel() {
    return myStatusPanel;
  }

  //
  // Misc
  //

  @Nullable
  @Override
  protected OpenFileDescriptor getOpenFileDescriptor() {
    return getCurrentSide().selectNotNull(getRequest().getContents()).getOpenFileDescriptor();
  }

  public static boolean canShowRequest(@NotNull DiffContext context, @NotNull DiffRequest request) {
    if (!(request instanceof ContentDiffRequest)) return false;

    List<DiffContent> contents = ((ContentDiffRequest)request).getContents();
    if (contents.size() != 2) return false;

    boolean canShow = true;
    boolean wantShow = false;
    for (DiffContent content : contents) {
      canShow &= canShowContent(content, context);
      wantShow |= wantShowContent(content, context);
    }
    return canShow && wantShow;
  }

  public static boolean canShowContent(@NotNull DiffContent content, @NotNull DiffContext context) {
    if (content instanceof EmptyContent) return true;
    if (content instanceof DocumentContent) return true;
    if (content instanceof FileContent) {
      Project project = context.getProject();
      if (project == null) project = ProjectManager.getInstance().getDefaultProject();
      VirtualFile file = ((FileContent)content).getFile();

      return FileEditorProviderManager.getInstance().getProviders(project, file).length != 0;
    }
    return false;
  }

  public static boolean wantShowContent(@NotNull DiffContent content, @NotNull DiffContext context) {
    if (content instanceof EmptyContent) return false;
    if (content instanceof FileContent) {
      if (content.getContentType() == null) return false;
      if (content.getContentType().isBinary()) return true;
      if (content.getContentType() instanceof UIBasedFileType) return true;
      return false;
    }
    return false;
  }

  //
  // Actions
  //

  private class MyFocusOppositePaneAction extends FocusOppositePaneAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      assert getEditor1() != null && getEditor2() != null;
      setCurrentSide(getCurrentSide().other());
      myContext.requestFocus();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(getEditor1() != null && getEditor2() != null);
    }
  }

  //
  // Helpers
  //

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
      return DiffUtil.getVirtualFile(myRequest, getCurrentSide());
    }
    return super.getData(dataId);
  }

  private static class MyStatusPanel extends StatusPanel {
    @Override
    protected int getChangesCount() {
      return -1;
    }
  }
}
