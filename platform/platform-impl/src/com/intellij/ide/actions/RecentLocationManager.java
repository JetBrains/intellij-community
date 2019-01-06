// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.codeInsight.breadcrumbs.FileBreadcrumbsCollector;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerPosition;
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
import com.intellij.openapi.fileEditor.impl.text.TextEditorState;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
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

public class RecentLocationManager implements ProjectComponent {
  private static final int BEFORE_AFTER_LINES_COUNT = Registry.intValue("recent.locations.lines.before.and.after", 2);
  static final int LIST_SIZE = Registry.intValue("recent.locations.list.size", 10);
  @NotNull
  private final Project myProject;
  @NotNull
  private final Map<IdeDocumentHistoryImpl.PlaceInfo, PlaceInfoPersistentItem> myItems = new RecentLocationFixedSizeHashMap();

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
        List<IdeDocumentHistoryImpl.PlaceInfo> toRemove = ContainerUtil.filter(myItems.keySet(), placeInfo -> events.stream()
          .anyMatch(event -> event.isFromRefresh() && placeInfo.getFile().equals(event.getFile())));

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

    Collection<Integer> lines = ((TextEditorState)navigationState).getCaretLines();
    Integer line = ContainerUtil.getFirstItem(lines);
    if (line == null) {
      return;
    }

    Collection<Iterable<? extends Crumb>> result = getBreadcrumbs(project, editor, changePlace, line);

    Document document = editor.getDocument();
    PlaceInfoPersistentItem item = myItems.get(changePlace);
    RangeMarker rangeMarker = item != null ? item.getRangeMarker() : document.createRangeMarker(getLinesRange(document, line));

    VirtualFile file = changePlace.getFile();
    SyntaxHighlighter syntaxHighlighter =
      SyntaxHighlighterFactory.getSyntaxHighlighter(FileTypeManager.getInstance().getFileTypeByFile(file), myProject, file);

    LexerPosition position = null;
    if (syntaxHighlighter != null) {
      Lexer lexer = syntaxHighlighter.getHighlightingLexer();
      if (StringUtil.isEmpty(lexer.getBufferSequence())) {
        lexer.start(editor.getDocument().getCharsSequence());
      }
      position = lexer.getCurrentPosition();
    }

    myItems.put(changePlace, new PlaceInfoPersistentItem(result, rangeMarker, position, editor.getColorsScheme()));
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
    if (lineCount == 0) {
      return Pair.create(0, 0);
    }

    int before = Math.min(BEFORE_AFTER_LINES_COUNT, line);
    int after = Math.min(BEFORE_AFTER_LINES_COUNT, lineCount - line - 1);

    int linesBefore = before + BEFORE_AFTER_LINES_COUNT - after;
    int linesAfter = after + BEFORE_AFTER_LINES_COUNT - before;

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
  Collection<Iterable<? extends Crumb>> getBreadcrumbs(@NotNull IdeDocumentHistoryImpl.PlaceInfo placeInfo) {
    PlaceInfoPersistentItem item = myItems.get(placeInfo);
    if (item == null) {
      return ContainerUtil.emptyList();
    }

    return item.getResult();
  }

  @Nullable
  RangeMarker getRangeMarker(@NotNull IdeDocumentHistoryImpl.PlaceInfo placeInfo) {
    PlaceInfoPersistentItem item = myItems.get(placeInfo);
    if (item == null) {
      return null;
    }
    return item.getRangeMarker();
  }

  @Nullable
  LexerPosition getLexerPosition(@NotNull IdeDocumentHistoryImpl.PlaceInfo placeInfo) {
    PlaceInfoPersistentItem item = myItems.get(placeInfo);
    if (item == null) {
      return null;
    }
    return item.getPosition();
  }

  @Nullable
  EditorColorsScheme getColorScheme(@NotNull IdeDocumentHistoryImpl.PlaceInfo placeInfo) {
    PlaceInfoPersistentItem item = myItems.get(placeInfo);
    if (item == null) {
      return null;
    }
    return item.getScheme();
  }

  private static class PlaceInfoPersistentItem {
    private final Collection<Iterable<? extends Crumb>> myResult;
    @NotNull private final RangeMarker myRangeMarker;
    @Nullable private final LexerPosition myPosition;
    private EditorColorsScheme myScheme;

    PlaceInfoPersistentItem(@NotNull Collection<Iterable<? extends Crumb>> crumbs,
                            @NotNull RangeMarker rangeMarker,
                            @Nullable LexerPosition position,
                            @NotNull EditorColorsScheme scheme) {
      myResult = crumbs;
      myRangeMarker = rangeMarker;
      myPosition = position;
      myScheme = scheme;
    }

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

    @Nullable
    private LexerPosition getPosition() {
      return myPosition;
    }
  }

  private static class RecentLocationFixedSizeHashMap extends LinkedHashMap<IdeDocumentHistoryImpl.PlaceInfo, PlaceInfoPersistentItem> {
    @Override
    protected boolean removeEldestEntry(Map.Entry<IdeDocumentHistoryImpl.PlaceInfo, PlaceInfoPersistentItem> eldest) {
      return size() >= LIST_SIZE;
    }
  }
}
