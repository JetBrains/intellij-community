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
package com.intellij.ide.diff;

import com.intellij.ide.presentation.VirtualFilePresentation;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * @author Konstantin Bulenkov
 */
public class VirtualFileDiffElement extends DiffElement<VirtualFile> {
  private final VirtualFile myFile;
  private FileEditor myFileEditor;
  private FileEditorProvider myEditorProvider;

  public VirtualFileDiffElement(@NotNull VirtualFile file) {
    myFile = file;
  }

  @Override
  public String getPath() {
    return myFile.getPresentableUrl();
  }

  @NotNull
  @Override
  public String getName() {
    return myFile.getName();
  }

  @Override
  public String getPresentablePath() {
    return getPath();
  }

  @Override
  public long getSize() {
    return myFile.getLength();
  }

  @Override
  public long getTimeStamp() {
    return myFile.getTimeStamp();
  }

  @Override
  public boolean isContainer() {
    return myFile.isDirectory();
  }

  @Override
  @Nullable
  public OpenFileDescriptor getOpenFileDescriptor(@Nullable Project project) {
    if (project == null || project.isDefault()) return null;
    return new OpenFileDescriptor(project, myFile);
  }

  @Override
  public VirtualFileDiffElement[] getChildren() {
    if (myFile.is(VFileProperty.SYMLINK)) {
      return new VirtualFileDiffElement[0];
    }
    final VirtualFile[] files = myFile.getChildren();
    final ArrayList<VirtualFileDiffElement> elements = new ArrayList<VirtualFileDiffElement>();
    for (VirtualFile file : files) {
      if (!FileTypeManager.getInstance().isFileIgnored(file) && file.isValid()) {
        elements.add(new VirtualFileDiffElement(file));
      }
    }
    return elements.toArray(new VirtualFileDiffElement[elements.size()]);
  }

  @Override
  public byte[] getContent() throws IOException {
    return myFile.contentsToByteArray();
  }

  @Override
  public VirtualFile getValue() {
    return myFile;
  }

  @Override
  public Icon getIcon() {
    return isContainer() ? PlatformIcons.FOLDER_ICON : VirtualFilePresentation.getIcon(myFile);
  }

  @Override
  public Callable<DiffElement<VirtualFile>> getElementChooser(final Project project) {
    return new Callable<DiffElement<VirtualFile>>() {
      @Nullable
      @Override
      public DiffElement<VirtualFile> call() throws Exception {
        final FileChooserDescriptor descriptor = getChooserDescriptor();
        final VirtualFile[] result = FileChooser.chooseFiles(descriptor, project, getValue());
        return result.length == 1 ? createElement(result[0]) : null;
      }
    };
  }

  @Nullable
  protected VirtualFileDiffElement createElement(VirtualFile file) {
    return new VirtualFileDiffElement(file);
  }

  protected FileChooserDescriptor getChooserDescriptor() {
    return new FileChooserDescriptor(false, true, false, false, false, false);
  }

  @Override
  protected JComponent getFromProviders(Project project, DiffElement target) {
    if (project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }
    final FileEditorProvider[] providers = FileEditorProviderManager.getInstance().getProviders(project, getValue());
    if (providers.length > 0) {
      myFileEditor = providers[0].createEditor(project, getValue());
      myEditorProvider = providers[0];
      setCustomState(myFileEditor);
      return myFileEditor.getComponent();
    }
    return null;
  }

  private static void setCustomState(FileEditor editor) {
    final FileEditorState state = editor.getState(FileEditorStateLevel.FULL);
    if (state instanceof TransferableFileEditorState) {
      final TransferableFileEditorState editorState = (TransferableFileEditorState)state;
      final String id = editorState.getEditorId();
      final HashMap<String, String> options = new HashMap<String, String>();
      final PropertiesComponent properties = PropertiesComponent.getInstance();
      for (String key : editorState.getTransferableOptions().keySet()) {
        final String value = properties.getValue(getKey(id, key));
        if (value != null) {
          options.put(key, value);
        }
      }
      editorState.setTransferableOptions(options);
      editor.setState(editorState);
    }
  }

  private static void saveCustomState(FileEditor editor) {
    final FileEditorState state = editor.getState(FileEditorStateLevel.FULL);
    if (state instanceof TransferableFileEditorState) {
      final TransferableFileEditorState editorState = (TransferableFileEditorState)state;
      final String id = editorState.getEditorId();
      final PropertiesComponent properties = PropertiesComponent.getInstance();
      final Map<String,String> options = editorState.getTransferableOptions();
      for (String key : options.keySet()) {
        properties.setValue(getKey(id, key), options.get(key));
      }
    }
  }

  private static String getKey(String editorId, String key) {
    return "dir.diff.editor.options." + editorId + "." + key;
  }

  @Override
  protected DiffRequest createRequestForBinaries(Project project, @NotNull VirtualFile src, @NotNull VirtualFile trg) {
    if (project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }
    if (FileEditorProviderManager.getInstance().getProviders(project, src).length > 0
        && FileEditorProviderManager.getInstance().getProviders(project, trg).length > 0) {
      return super.createRequestForBinaries(project, src, trg);
    } else {
      return null;
    }
  }

  @Override
  public void disposeViewComponent() {
    super.disposeViewComponent();
    if (myFileEditor != null && myEditorProvider != null) {
      saveCustomState(myFileEditor);
      myEditorProvider.disposeEditor(myFileEditor);
      myFileEditor = null;
      myEditorProvider = null;
    }
  }

  @Override
  public DataProvider getDataProvider(final Project project) {
    return new DataProvider() {
      @Override
      public Object getData(@NonNls String dataId) {
        if (CommonDataKeys.PROJECT.is(dataId)) {
          return project;
        }
        if (PlatformDataKeys.FILE_EDITOR.is(dataId)) {
          return myFileEditor;
        }
        return null;
      }
    };
  }

  @Override
  public boolean isOperationsEnabled() {
    return myFile.getFileSystem() instanceof LocalFileSystem;
  }

  @Override
  public VirtualFileDiffElement copyTo(DiffElement<VirtualFile> container, String relativePath) {
    try {
      final File src = new File(myFile.getPath());
      final File trg = new File(container.getValue().getPath() + relativePath + src.getName());
      FileUtil.copy(src, trg);
      final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(trg);
      if (virtualFile != null) {
        return new VirtualFileDiffElement(virtualFile);
      }
    }
    catch (IOException e) {//
    }
    return null;
  }

  @Override
  public boolean delete() {
    try {
      myFile.delete(this);
    }
    catch (IOException e) {
      return false;
    }
    return true;
  }

  @Override
  public void refresh(boolean userInitiated) {
    refreshFile(userInitiated, myFile);
  }

  public static void refreshFile(boolean userInitiated, VirtualFile virtualFile) {
    if (userInitiated) {
      final List<Document> docsToSave = new ArrayList<Document>();
      final FileDocumentManager manager = FileDocumentManager.getInstance();
      for (Document document : manager.getUnsavedDocuments()) {
        VirtualFile file = manager.getFile(document);
        if (file != null && VfsUtilCore.isAncestor(virtualFile, file, false)) {
          docsToSave.add(document);
        }
      }

      if (!docsToSave.isEmpty()) {
        new WriteAction() {
          @Override
          protected void run(@NotNull Result result) throws Throwable {
            for (Document document : docsToSave) {
              manager.saveDocument(document);
            }
          }
        }.execute();
      }

      VfsUtil.markDirtyAndRefresh(true, true, true, virtualFile);
    }
  }
}
