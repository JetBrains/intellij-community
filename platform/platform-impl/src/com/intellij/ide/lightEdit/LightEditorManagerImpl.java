// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public final class LightEditorManagerImpl implements LightEditorManager, Disposable {
  private static final Logger LOG = Logger.getInstance(LightEditorManagerImpl.class);

  private final List<LightEditorInfo>                myEditors         = new CopyOnWriteArrayList<>();
  private final EventDispatcher<LightEditorListener> myEventDispatcher =
    EventDispatcher.create(LightEditorListener.class);

  private final LightEditServiceImpl myLightEditService;

  final static Key<Boolean> NO_IMPLICIT_SAVE = Key.create("light.edit.no.implicit.save");

  private final static String DEFAULT_FILE_NAME = "untitled_";

  public LightEditorManagerImpl(LightEditServiceImpl service) {
    myLightEditService = service;
  }

  private @Nullable LightEditorInfo doCreateEditor(@NotNull VirtualFile file) {
    Project project = Objects.requireNonNull(LightEditUtil.getProject());
    Pair<FileEditorProvider, FileEditor> pair = createFileEditor(project, file);
    if (pair == null) {
      return null;
    }
    LightEditorInfo editorInfo = new LightEditorInfoImpl(pair.first, pair.second, file);
    ObjectUtils.consumeIfNotNull(EditorHistoryManager.getInstance(project).getState(file, pair.first),
                                 state -> editorInfo.getFileEditor().setState(state));
    ObjectUtils.consumeIfCast(LightEditorInfoImpl.getEditor(editorInfo), EditorImpl.class,
                              editorImpl -> editorImpl.setDropHandler(new LightEditDropHandler()));
    myEditors.add(editorInfo);
    project.getMessageBus().syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER).fileOpened(
      FileEditorManager.getInstance(project), file
    );
    return editorInfo;
  }

  private static @Nullable Pair<FileEditorProvider, FileEditor> createFileEditor(@NotNull Project project, @NotNull VirtualFile file) {
    FileEditorProvider[] providers = FileEditorProviderManager.getInstance().getProviders(project, file);
    for (FileEditorProvider provider : providers) {
      FileEditor editor = provider.createEditor(project, file);
      return Pair.create(provider, editor);
    }
    return null;
  }

  /**
   * Create an empty editor without any file type assigned (defaults to plain text).
   *
   * @return The newly created editor info.
   */
  @Override
  public @NotNull LightEditorInfo createNewEditor(@Nullable String preferredName) {
    String name = preferredName != null ? preferredName : getUniqueName();
    LightVirtualFile file = new LightVirtualFile(name);
    file.setFileType(PlainTextFileType.INSTANCE);
    return Objects.requireNonNull(doCreateEditor(file));
  }

  @Override
  public @Nullable LightEditorInfo createEditor(@NotNull VirtualFile file) {
    LightEditFileTypeOverrider.markUnknownFileTypeAsPlainText(file);
    setImplicitSaveEnabled(file, false);
    LightEditorInfo editorInfo = doCreateEditor(file);
    Editor editor = LightEditorInfoImpl.getEditor(editorInfo);
    if (editor instanceof EditorEx) ((EditorEx)editor).setHighlighter(getHighlighter(file, editor));
    return editorInfo;
  }

  private static void setImplicitSaveEnabled(@NotNull VirtualFile file, boolean isEnabled) {
    Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document != null) {
      document.putUserData(NO_IMPLICIT_SAVE, isEnabled ? null : true);
    }
  }

  @Override
  public void dispose() {
    releaseEditors();
  }

  public void releaseEditors() {
    myEditors.forEach(editorInfo -> ((LightEditorInfoImpl)editorInfo).disposeEditor());
    myEditors.clear();
  }

  @Override
  public void closeEditor(@NotNull LightEditorInfo editorInfo) {
    EditorHistoryManager.getInstance(myLightEditService.getOrCreateProject()).updateHistoryEntry(editorInfo.getFile(), false);
    myEditors.remove(editorInfo);
    setImplicitSaveEnabled(editorInfo.getFile(), true);
    ((LightEditorInfoImpl)editorInfo).disposeEditor();
    myEventDispatcher.getMulticaster().afterClose(editorInfo);
  }

  @Override
  public void addListener(@NotNull LightEditorListener listener) {
    myEventDispatcher.addListener(listener);
  }

  @Override
  public void addListener(@NotNull LightEditorListener listener, @NotNull Disposable parent) {
    myEventDispatcher.addListener(listener, parent);
  }

  void fireEditorSelected(@Nullable LightEditorInfo editorInfo) {
    myEventDispatcher.getMulticaster().afterSelect(editorInfo);
  }

  void fireAutosaveModeChanged(boolean autosaveMode) {
    myEventDispatcher.getMulticaster().autosaveModeChanged(autosaveMode);
  }

  void fireFileStatusChanged(@NotNull Collection<LightEditorInfo> editorInfos) {
    myEventDispatcher.getMulticaster().fileStatusChanged(editorInfos);
  }

  @NotNull
  private static EditorHighlighter getHighlighter(@NotNull VirtualFile file, @NotNull Editor editor) {
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(file, editor.getColorsScheme(), null);
  }

  int getEditorCount() {
    return myEditors.size();
  }

  @Nullable
  public LightEditorInfo findOpen(@NotNull VirtualFile file) {
    return myEditors.stream()
      .filter(editorInfo -> file.getPath().equals(editorInfo.getFile().getPath()))
      .findFirst().orElse(null);
  }

  @Override
  public boolean isImplicitSaveAllowed(@NotNull Document document) {
    return LightEditService.getInstance().isAutosaveMode() ||
           !ObjectUtils.notNull(document.getUserData(NO_IMPLICIT_SAVE), false);
  }

  @Override
  @NotNull
  public Collection<VirtualFile> getOpenFiles() {
    return myEditors.stream().map(info -> info.getFile()).collect(Collectors.toSet());
  }

  @Override
  public boolean isFileOpen(@NotNull VirtualFile file) {
    return myEditors.stream().anyMatch(editorInfo -> file.equals(editorInfo.getFile()));
  }

  @Override
  public boolean containsUnsavedDocuments() {
    return myEditors.stream().anyMatch(editorInfo -> editorInfo.isUnsaved());
  }

  @NotNull
  List<LightEditorInfo> getUnsavedEditors() {
    return ContainerUtil.filter(myEditors, editorInfo -> editorInfo.isUnsaved());
  }

  private String getUniqueName() {
    for (int i = 1; ; i++) {
      String candidate = DEFAULT_FILE_NAME + i;
      if (myEditors.stream().noneMatch(editorInfo -> editorInfo.getFile().getName().equals(candidate))) {
        return candidate;
      }
    }
  }

  @Override
  @NotNull
  public LightEditorInfo saveAs(@NotNull LightEditorInfo info, @NotNull VirtualFile targetFile) {
    LightEditorInfo newInfo = createEditor(targetFile);
    if (newInfo != null) {
      ApplicationManager.getApplication().runWriteAction(() -> {
        FileDocumentManager manager = FileDocumentManager.getInstance();
        Document source = manager.getDocument(info.getFile());
        Document target = manager.getDocument(targetFile);
        if (source == null) {
          LOG.error("Cannot save to " + targetFile + ": no document found for " + info.getFile());
          return;
        }
        if (target == null) {
          LOG.error("Cannot save to " + targetFile + ": no document found for " + targetFile);
          return;
        }
        targetFile.refresh(false, false); // to avoid memory-disk conflict if target file was changed externally
        target.setText(source.getCharsSequence());
        manager.saveDocument(target);
      });
      return newInfo;
    }
    return info;
  }

  @Nullable
  LightEditorInfo getEditorInfo(@NotNull VirtualFile file) {
    return myEditors.stream().filter(editorInfo -> file.equals(editorInfo.getFile())).findFirst().orElse(null);
  }
}
