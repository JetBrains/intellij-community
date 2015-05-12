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
import com.intellij.diff.contents.*;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.util.base.ListenerDiffViewerBase;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
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
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.AnimatedIcon;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class BinaryDiffViewer extends ListenerDiffViewerBase {
  public static final Logger LOG = Logger.getInstance(BinaryDiffViewer.class);

  @NotNull private final BinaryDiffPanel myPanel;
  @NotNull private final BinaryContentPanel myContentPanel;
  @NotNull private final MyStatusPanel myStatusPanel;

  @Nullable private final FileEditor myEditor1;
  @Nullable private final FileEditor myEditor2;
  @Nullable private final FileEditorProvider myEditorProvider1;
  @Nullable private final FileEditorProvider myEditorProvider2;

  @Nullable private final MyEditorFocusListener myEditorFocusListener1;
  @Nullable private final MyEditorFocusListener myEditorFocusListener2;

  @NotNull private Side myCurrentSide = Side.LEFT;

  public BinaryDiffViewer(@NotNull DiffContext context, @NotNull DiffRequest request) {
    super(context, (ContentDiffRequest)request);

    List<JComponent> titlePanel = DiffUtil.createSimpleTitles(myRequest);
    Couple<Pair<FileEditor, FileEditorProvider>> editors = createEditors();

    myEditor1 = editors.first.first;
    myEditorProvider1 = editors.first.second;
    myEditor2 = editors.second.first;
    myEditorProvider2 = editors.second.second;

    if (myEditor1 != null && myEditor2 != null) {
      myEditorFocusListener1 = new MyEditorFocusListener(Side.LEFT);
      myEditorFocusListener2 = new MyEditorFocusListener(Side.RIGHT);
    }
    else {
      myEditorFocusListener1 = null;
      myEditorFocusListener2 = null;
    }


    myContentPanel = new BinaryContentPanel(titlePanel, myEditor1, myEditor2);

    myPanel = new BinaryDiffPanel(this, myContentPanel, this, context);
    if (myEditor1 == null && myEditor2 == null) myPanel.setErrorContent();

    myStatusPanel = new MyStatusPanel();

    new MyFocusOppositePaneAction().setupAction(myPanel);


    installEditorListeners();
  }

  @Override
  protected void onInit() {
    super.onInit();
    processContextHints();
  }

  @Override
  public void onDispose() {
    updateContextHints();
    destroyEditorListeners();
    destroyEditors();
    super.onDispose();
  }

  private void processContextHints() {
    if (myEditor1 == null) {
      myCurrentSide = Side.RIGHT;
    }
    else if (myEditor2 == null) {
      myCurrentSide = Side.LEFT;
    }
    else {
      Side side = myContext.getUserData(DiffUserDataKeys.PREFERRED_FOCUS_SIDE);
      if (side != null) myCurrentSide = side;
    }
  }

  private void updateContextHints() {
    if (myEditor1 != null && myEditor2 != null) {
      myContext.putUserData(DiffUserDataKeys.PREFERRED_FOCUS_SIDE, myCurrentSide);
    }
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
    if (myEditor1 != null) {
      assert myEditorProvider1 != null;
      myEditorProvider1.disposeEditor(myEditor1);
    }
    if (myEditor2 != null) {
      assert myEditorProvider2 != null;
      myEditorProvider2.disposeEditor(myEditor2);
    }
  }

  private void installEditorListeners() {
    if (myEditor1 != null && myEditor2 != null) {
      myEditor1.getComponent().addFocusListener(myEditorFocusListener1);
      myEditor2.getComponent().addFocusListener(myEditorFocusListener2);
    }
  }

  private void destroyEditorListeners() {
    if (myEditor1 != null && myEditor2 != null) {
      myEditor1.getComponent().removeFocusListener(myEditorFocusListener1);
      myEditor2.getComponent().removeFocusListener(myEditorFocusListener2);
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
        return new Runnable() {
          @Override
          public void run() {
            clearDiffPresentation();
            myPanel.addInsertedContentNotification();
          }
        };
      }

      if (contents.get(1) instanceof EmptyContent) {
        return new Runnable() {
          @Override
          public void run() {
            clearDiffPresentation();
            myPanel.addRemovedContentNotification();
          }
        };
      }

      if (!(contents.get(0) instanceof FileContent) || !(contents.get(1) instanceof FileContent)) {
        return new Runnable() {
          @Override
          public void run() {
            clearDiffPresentation();
          }
        };
      }

      final VirtualFile file1 = ((FileContent)contents.get(0)).getFile();
      final VirtualFile file2 = ((FileContent)contents.get(1)).getFile();
      if (!file1.isValid() || !file2.isValid()) {
        return new Runnable() {
          @Override
          public void run() {
            myPanel.addDiffErrorNotification();
            clearDiffPresentation();
          }
        };
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

      return new Runnable() {
        @Override
        public void run() {
          clearDiffPresentation();
          if (equal) myPanel.addContentsEqualNotification();
        }
      };
    }
    catch (ProcessCanceledException ignore) {
      return new Runnable() {
        @Override
        public void run() {
          clearDiffPresentation();
          myPanel.addOperationCanceledNotification();
        }
      };
    }
    catch (Throwable e) {
      LOG.error(e);
      return new Runnable() {
        @Override
        public void run() {
          clearDiffPresentation();
          myPanel.addDiffErrorNotification();
        }
      };
    }
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
    return myPanel.getPreferredFocusedComponent();
  }

  @NotNull
  Side getCurrentSide() {
    return myCurrentSide;
  }

  @Nullable
  FileEditor getEditor2() {
    return myEditor2;
  }

  @Nullable
  FileEditor getEditor1() {
    return myEditor1;
  }

  @Nullable
  FileEditor getCurrentEditor() {
    return getCurrentSide().select(myEditor1, myEditor2);
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
    ContentDiffRequest request = getRequest();
    FileEditor editor = getCurrentEditor();
    if (editor == null) return null;

    DiffContent content = getCurrentSide().selectNotNull(request.getContents());

    return content.getOpenFileDescriptor();
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
      assert myEditor1 != null && myEditor2 != null;
      myCurrentSide = myCurrentSide.other();
      myPanel.requestFocus();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(myEditor1 != null && myEditor2 != null);
    }
  }

  //
  // Helpers
  //

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
      return DiffUtil.getVirtualFile(myRequest, myCurrentSide);
    }
    return super.getData(dataId);
  }

  private static class MyStatusPanel extends JPanel {
    private final AnimatedIcon myBusySpinner;

    public MyStatusPanel() {
      super(new BorderLayout());
      myBusySpinner = new AsyncProcessIcon("StatusPanelSpinner");
      myBusySpinner.setVisible(false);

      add(myBusySpinner, BorderLayout.WEST);
      setBorder(IdeBorderFactory.createEmptyBorder(0, 4, 0, 4));
    }

    public void setBusy(boolean busy) {
      if (busy) {
        myBusySpinner.setVisible(true);
        myBusySpinner.resume();
      }
      else {
        myBusySpinner.setVisible(false);
        myBusySpinner.suspend();
      }
    }
  }

  private class MyEditorFocusListener extends FocusAdapter {
    @NotNull private final Side mySide;

    private MyEditorFocusListener(@NotNull Side side) {
      mySide = side;
    }

    public void focusGained(FocusEvent e) {
      if (myEditor1 == null || myEditor2 == null) return;
      myCurrentSide = mySide;
    }
  }
}
