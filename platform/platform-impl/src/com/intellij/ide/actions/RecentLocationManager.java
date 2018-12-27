// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.codeInsight.breadcrumbs.FileBreadcrumbsCollector;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite;
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl;
import com.intellij.openapi.fileEditor.impl.text.TextEditorState;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.ui.components.breadcrumbs.Crumb;
import com.intellij.util.CollectConsumer;
import com.intellij.util.DocumentUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class RecentLocationManager implements ProjectComponent {
  private static final int BEFORE_AFTER_LINES_COUNT = Registry.intValue("recent.locations.lines.before.and.after", 2);
  @NotNull
  private final Project myProject;
  @NotNull
  private final Map<IdeDocumentHistoryImpl.PlaceInfo, PlaceInfoPersistentItem> myItems = ContainerUtil.newHashMap();

  @NotNull
  public static RecentLocationManager getInstance(@NotNull Project project) {
    return project.getComponent(RecentLocationManager.class);
  }

  public RecentLocationManager(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void projectOpened() {
    MessageBusConnection connection = myProject.getMessageBus().connect();

    subscribeChangedPlaces(connection);
    subscribeRecentPlaces(connection);
    subscribeOnExternalChange(connection);
  }

  private void subscribeOnExternalChange(@NotNull MessageBusConnection connection) {
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        List<IdeDocumentHistoryImpl.PlaceInfo> toRemove =
          ContainerUtil.filter(myItems.keySet(),
                               placeInfo -> events.stream().anyMatch(event -> event.isFromRefresh() && placeInfo.getFile().equals(event.getFile())));

        toRemove.forEach(placeInfo -> removePlace(placeInfo));
      }
    });
  }

  private void subscribeRecentPlaces(@NotNull MessageBusConnection connection) {
    connection.subscribe(IdeDocumentHistoryImpl.RecentPlacesListener.TOPIC, new IdeDocumentHistoryImpl.RecentPlacesListener() {
      @Override
      public void recentPlacePushed(@NotNull IdeDocumentHistoryImpl.PlaceInfo changePlace) {
        update(changePlace, myProject);
      }

      @Override
      public void recentPlaceRemoved(@NotNull IdeDocumentHistoryImpl.PlaceInfo changePlace) {
        removePlace(changePlace);
      }
    });
  }

  private void subscribeChangedPlaces(@NotNull MessageBusConnection connection) {
    connection.subscribe(IdeDocumentHistoryImpl.ChangePlacesListener.TOPIC, new IdeDocumentHistoryImpl.ChangePlacesListener() {
      @Override
      public void changedPlacePushed(@NotNull IdeDocumentHistoryImpl.PlaceInfo changePlace) {
        update(changePlace, myProject);
      }

      @Override
      public void changedPlaceRemoved(@NotNull IdeDocumentHistoryImpl.PlaceInfo changePlace) {
        removePlace(changePlace);
      }
    });
  }

  private void removePlace(@NotNull IdeDocumentHistoryImpl.PlaceInfo placeToRemove) {
    myItems.remove(placeToRemove);
  }

  private void update(@NotNull IdeDocumentHistoryImpl.PlaceInfo changePlace, @NotNull Project project) {
    FileEditorState navigationState = changePlace.getNavigationState();
    if (!(navigationState instanceof TextEditorState)) {
      return;
    }

    Editor editor = findEditor(project, changePlace);
    if (editor == null) {
      return;
    }

    int lineNumber = ((TextEditorState)navigationState).getRelativeCaretPositionLine(editor);

    Collection<Iterable<? extends Crumb>> result = getBreadcrumbs(project, editor, changePlace, lineNumber);
    Document document = editor.getDocument();
    Pair<Integer, Integer> lines = getBoundLineNumbers(document, lineNumber);

    myItems.put(changePlace, new PlaceInfoPersistentItem(result, getText(document, lines, lineNumber), lines.getFirst()));
  }

  @Nullable
  private static Editor findEditor(@NotNull Project project,
                                   @NotNull IdeDocumentHistoryImpl.PlaceInfo changePlace) {
    JComponent component = FileEditorManagerEx.getInstanceEx(project).getPreferredFocusedComponent();
    if (!(component instanceof EditorComponentImpl)) {
      return null;
    }

    EditorWindow window = changePlace.getWindow();
    if (window == null) {
      return null;
    }

    EditorWithProviderComposite composite = window.findFileComposite(changePlace.getFile());
    if (composite == null) {
      return null;
    }

    FileEditor fileEditor = composite.getSelectedWithProvider().getFileEditor();
    if (!(fileEditor instanceof TextEditor)) {
      return null;
    }

    return ((TextEditor)fileEditor).getEditor();
  }

  @NotNull
  private static String getText(@NotNull Document document, @NotNull Pair<Integer, Integer> lines, int line) {
    TextRange linesRange = getLinesRange(document, DocumentUtil.getLineTextRange(document, line), lines.getFirst(), lines.getSecond());
    return document.getText(linesRange);
  }

  @NotNull
  private static Collection<Iterable<? extends Crumb>> getBreadcrumbs(@NotNull Project project,
                                                                      @NotNull Editor editor,
                                                                      @NotNull IdeDocumentHistoryImpl.PlaceInfo changePlace,
                                                                      int line) {
    FileBreadcrumbsCollector collector = FileBreadcrumbsCollector.findBreadcrumbsCollector(project, changePlace.getFile());
    Collection<Iterable<? extends Crumb>> result = ContainerUtil.emptyList();
    if (collector != null) {
      CollectConsumer<Iterable<? extends Crumb>> consumer = new CollectConsumer<>();
      int lineStartOffset = editor.getDocument().getLineStartOffset(line);
      collector.updateCrumbs(changePlace.getFile(), editor, lineStartOffset, new ProgressIndicatorBase(), consumer);
      result = consumer.getResult();
    }
    return result;
  }

  @NotNull
  private static Pair<Integer, Integer> getBoundLineNumbers(@NotNull Document document, int line) {
    int lineCount = document.getLineCount();
    int before = Math.min(BEFORE_AFTER_LINES_COUNT, line);
    int after = Math.min(BEFORE_AFTER_LINES_COUNT, lineCount - line - 1);

    int linesBefore = before + BEFORE_AFTER_LINES_COUNT - after;
    int linesAfter = after + BEFORE_AFTER_LINES_COUNT - before;

    int start = Math.max(line - linesBefore, 0);
    int end = Math.min(line + linesAfter, lineCount - 1);

    return Pair.create(start, end);
  }

  @NotNull
  private static TextRange getLinesRange(@NotNull Document document, @NotNull TextRange range, int start, int end) {
    int startOffset = DocumentUtil.getLineTextRange(document, start).getStartOffset();
    int endOffset = DocumentUtil.getLineTextRange(document, end).getEndOffset();

    return startOffset <= endOffset
           ? TextRange.create(startOffset, endOffset)
           : TextRange.create(range);
  }

  @NotNull
  Collection<Iterable<? extends Crumb>> getBreadcrumbs(@NotNull IdeDocumentHistoryImpl.PlaceInfo placeInfo) {
    PlaceInfoPersistentItem item = myItems.get(placeInfo);
    if (item == null) {
      return ContainerUtil.emptyList();
    }

    return item.getResult();
  }

  @Nullable
  String getTexts(@NotNull IdeDocumentHistoryImpl.PlaceInfo placeInfo) {
    PlaceInfoPersistentItem item = myItems.get(placeInfo);
    if (item == null) {
      return null;
    }

    return item.getText();
  }

  int getLineNumber(@NotNull IdeDocumentHistoryImpl.PlaceInfo placeInfo) {
    PlaceInfoPersistentItem item = myItems.get(placeInfo);
    if (item == null) {
      return -1;
    }

    return item.getLineNumber();
  }

  private static class PlaceInfoPersistentItem {
    private final Collection<Iterable<? extends Crumb>> myResult;
    private final String myText;
    private final int myStartLineNumber;

    PlaceInfoPersistentItem(@NotNull Collection<Iterable<? extends Crumb>> crumbs, @NotNull String text, int startLineNumber) {
      myResult = crumbs;
      myText = text;
      myStartLineNumber = startLineNumber;
    }

    @NotNull
    Collection<Iterable<? extends Crumb>> getResult() {
      return myResult;
    }

    @NotNull
    String getText() {
      return myText;
    }

    int getLineNumber() {
      return myStartLineNumber;
    }
  }
}
