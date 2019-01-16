// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.codeInsight.breadcrumbs.FileBreadcrumbsCollector;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite;
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl;
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl.PlaceInfo;
import com.intellij.openapi.fileEditor.impl.text.TextEditorState;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.ui.components.breadcrumbs.Crumb;
import com.intellij.util.CollectConsumer;
import com.intellij.util.DocumentUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RecentLocationManager implements ProjectComponent {
  @NotNull private final Project myProject;
  @NotNull private final Map<PlaceInfo, PlaceInfoPersistentItem> myRecentItems = new RecentLocationFixedSizeHashMap();
  @NotNull private final Map<PlaceInfo, PlaceInfoPersistentItem> myChangedItems = new RecentLocationFixedSizeHashMap();

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
        removeInvalidPlaces(events, myChangedItems);
        removeInvalidPlaces(events, myRecentItems);
      }
    });
  }

  private static void removeInvalidPlaces(@NotNull List<? extends VFileEvent> events,
                                          @NotNull Map<PlaceInfo, PlaceInfoPersistentItem> items) {
    List<PlaceInfo> toRemove = ContainerUtil.filter(items.keySet(), placeInfo -> events.stream()
      .anyMatch(event -> event.isFromRefresh() && placeInfo.getFile().equals(event.getFile())));

    toRemove.forEach(placeInfo -> removePlace(placeInfo, items));
  }

  private void subscribeRecentPlaces(@NotNull MessageBusConnection connection) {
    connection.subscribe(IdeDocumentHistoryImpl.RecentPlacesListener.TOPIC, new IdeDocumentHistoryImpl.RecentPlacesListener() {
      @Override
      public void recentPlacePushed(@NotNull PlaceInfo changePlace) {
        update(changePlace, myProject, myRecentItems);
      }

      @Override
      public void recentPlaceRemoved(@NotNull PlaceInfo changePlace) {
        removePlace(changePlace, myRecentItems);
      }
    });
  }

  private void subscribeChangedPlaces(@NotNull MessageBusConnection connection) {
    connection.subscribe(IdeDocumentHistoryImpl.ChangePlacesListener.TOPIC, new IdeDocumentHistoryImpl.ChangePlacesListener() {
      @Override
      public void changedPlacePushed(@NotNull PlaceInfo changePlace) {
        update(changePlace, myProject, myChangedItems);
      }

      @Override
      public void changedPlaceRemoved(@NotNull PlaceInfo changePlace) {
        removePlace(changePlace, myChangedItems);
      }
    });
  }

  private static void removePlace(@NotNull PlaceInfo placeToRemove, @NotNull Map<PlaceInfo, PlaceInfoPersistentItem> items) {
    items.remove(placeToRemove);
  }

  private static void update(@NotNull PlaceInfo changePlace,
                             @NotNull Project project,
                             @NotNull Map<PlaceInfo, PlaceInfoPersistentItem> items) {
    Editor editor = findEditor(project, changePlace);
    if (editor == null) {
      return;
    }

    int line = getLineNumber(changePlace);
    if (line == -1) {
      return;
    }

    Document document = editor.getDocument();
    TextRange range = getLinesRange(document, line);

    String text = document.getText(TextRange.create(range.getStartOffset(), range.getEndOffset()));

    int newLinesBefore = StringUtil.countNewLines(
      Objects.requireNonNull(StringUtil.substringBefore(text, StringUtil.trimLeading(text, '\n'))));
    int newLinesAfter = StringUtil.countNewLines(
      Objects.requireNonNull(StringUtil.substringAfter(text, StringUtil.trimTrailing(text, '\n'))));

    int firstLine = document.getLineNumber(range.getStartOffset());
    int firstLineAdjusted = firstLine + newLinesBefore;

    int lastLine = document.getLineNumber(range.getEndOffset());
    int lastLineAdjusted = lastLine - newLinesAfter;

    int startOffset = document.getLineStartOffset(firstLineAdjusted);
    int endOffset = document.getLineEndOffset(lastLineAdjusted);

    items.put(changePlace, new PlaceInfoPersistentItem(getBreadcrumbs(project, editor, changePlace, line),
                                                       document.createRangeMarker(startOffset, endOffset),
                                                       editor.getColorsScheme()));
  }

  private static int getLineNumber(@NotNull PlaceInfo changePlace) {
    FileEditorState navigationState = changePlace.getNavigationState();
    if (!(navigationState instanceof TextEditorState)) {
      return -1;
    }

    Collection<Integer> lines = ((TextEditorState)navigationState).getCaretLines();
    Integer line = ContainerUtil.getFirstItem(lines);
    return line == null ? -1 : line;
  }

  @Nullable
  private static Editor findEditor(@NotNull Project project, @NotNull PlaceInfo changePlace) {
    JComponent component = FileEditorManagerEx.getInstanceEx(project).getPreferredFocusedComponent();
    if (component == null || !component.isShowing() || !(component instanceof EditorComponentImpl)) {
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
  private static Collection<Iterable<? extends Crumb>> getBreadcrumbs(@NotNull Project project,
                                                                      @NotNull Editor editor,
                                                                      @NotNull PlaceInfo changePlace,
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
    if (lineCount == 0) {
      return Pair.create(0, 0);
    }

    int beforeAfterLinesCount = Registry.intValue("recent.locations.lines.before.and.after", 2);
    int before = Math.min(beforeAfterLinesCount, line);
    int after = Math.min(beforeAfterLinesCount, lineCount - line - 1);

    int linesBefore = before + beforeAfterLinesCount - after;
    int linesAfter = after + beforeAfterLinesCount - before;

    int start = Math.max(line - linesBefore, 0);
    int end = Math.min(line + linesAfter, lineCount - 1);

    return Pair.create(start, end);
  }

  @NotNull
  private static TextRange getLinesRange(@NotNull Document document, int line) {
    Pair<Integer, Integer> lines = getBoundLineNumbers(document, line);

    int startOffset = DocumentUtil.getLineTextRange(document, lines.getFirst()).getStartOffset();
    int endOffset = DocumentUtil.getLineTextRange(document, lines.getSecond()).getEndOffset();

    return startOffset <= endOffset
           ? TextRange.create(startOffset, endOffset)
           : TextRange.create(DocumentUtil.getLineTextRange(document, line));
  }

  @NotNull
  private Map<PlaceInfo, PlaceInfoPersistentItem> getMap(boolean showChanged) {
    return showChanged ? myChangedItems : myRecentItems;
  }

  @NotNull
  Collection<Iterable<? extends Crumb>> getBreadcrumbs(@NotNull PlaceInfo placeInfo, boolean showChanged) {
    PlaceInfoPersistentItem item = getMap(showChanged).get(placeInfo);
    return item == null ? ContainerUtil.emptyList() : item.getResult();
  }

  @Nullable
  RangeMarker getRangeMarker(@NotNull PlaceInfo placeInfo, boolean showChanged) {
    PlaceInfoPersistentItem item = getMap(showChanged).get(placeInfo);
    return item == null ? null : item.getRangeMarker();
  }

  @Nullable
  EditorColorsScheme getColorScheme(@NotNull PlaceInfo placeInfo, boolean showChanged) {
    PlaceInfoPersistentItem item = getMap(showChanged).get(placeInfo);
    return item == null ? null : item.getScheme();
  }

  private static class PlaceInfoPersistentItem {
    private final Collection<Iterable<? extends Crumb>> myResult;
    @NotNull private final RangeMarker myRangeMarker;
    @NotNull private final EditorColorsScheme myScheme;

    PlaceInfoPersistentItem(@NotNull Collection<Iterable<? extends Crumb>> crumbs,
                            @NotNull RangeMarker rangeMarker,
                            @NotNull EditorColorsScheme scheme) {
      myResult = crumbs;
      myRangeMarker = rangeMarker;
      myScheme = scheme;
    }

    @NotNull
    private EditorColorsScheme getScheme() {
      return myScheme;
    }

    @NotNull
    private Collection<Iterable<? extends Crumb>> getResult() {
      return myResult;
    }

    @NotNull
    private RangeMarker getRangeMarker() {
      return myRangeMarker;
    }
  }

  private static class RecentLocationFixedSizeHashMap extends LinkedHashMap<PlaceInfo, PlaceInfoPersistentItem> {
    @Override
    protected boolean removeEldestEntry(Map.Entry<PlaceInfo, PlaceInfoPersistentItem> eldest) {
      return size() > Registry.intValue("recent.locations.list.size", 10);
    }
  }
}
