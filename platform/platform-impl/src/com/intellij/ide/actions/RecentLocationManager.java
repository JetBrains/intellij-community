// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.codeInsight.breadcrumbs.FileBreadcrumbsCollector;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
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
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.ui.components.breadcrumbs.Crumb;
import com.intellij.util.CollectConsumer;
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

    subscribeRecentPlaces(connection);
    subscribeOnExternalChange(connection);
  }

  @NotNull
  Collection<Iterable<? extends Crumb>> getBreadcrumbs(@NotNull PlaceInfo placeInfo, boolean showChanged) {
    PlaceInfoPersistentItem item = getMap(showChanged).get(placeInfo);
    return item == null ? ContainerUtil.emptyList() : item.getCrumbs();
  }

  @Nullable
  RangeMarker getPositionOffset(@NotNull PlaceInfo placeInfo, boolean showChanged) {
    PlaceInfoPersistentItem item = getMap(showChanged).get(placeInfo);
    return item == null ? null : item.getPositionOffset();
  }

  @Nullable
  EditorColorsScheme getColorScheme(@NotNull PlaceInfo placeInfo, boolean showChanged) {
    PlaceInfoPersistentItem item = getMap(showChanged).get(placeInfo);
    return item == null ? null : item.getScheme();
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
      public void recentPlaceAdded(@NotNull PlaceInfo changePlace, boolean isChanged) {
        update(changePlace, myProject, getItems(isChanged));
      }

      @Override
      public void recentPlaceRemoved(@NotNull PlaceInfo changePlace, boolean isChanged) {
        removePlace(changePlace, getItems(isChanged));
      }

      @NotNull
      public Map<PlaceInfo, PlaceInfoPersistentItem> getItems(boolean isChanged) {
        return isChanged ? myChangedItems : myRecentItems;
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

    LogicalPosition logicalPosition = getLogicalPosition(changePlace);
    if (logicalPosition == null) {
      return;
    }

    int offset = editor.logicalPositionToOffset(logicalPosition);
    items.put(changePlace, new PlaceInfoPersistentItem(getBreadcrumbs(project, editor, changePlace, logicalPosition),
                                                       editor.getDocument().createRangeMarker(offset, offset),
                                                       editor.getColorsScheme()));
  }

  @Nullable
  private static LogicalPosition getLogicalPosition(@NotNull PlaceInfo changePlace) {
    FileEditorState navigationState = changePlace.getNavigationState();
    if (!(navigationState instanceof TextEditorState)) {
      return null;
    }

    Collection<Integer> lines = ((TextEditorState)navigationState).getCaretLines();
    Integer line = ContainerUtil.getFirstItem(lines);

    Collection<Integer> caretColumns = ((TextEditorState)navigationState).getCaretColumns();
    Integer column = ContainerUtil.getFirstItem(caretColumns);

    return line != null && column != null ? new LogicalPosition(line, column) : null;
  }

  @Nullable
  private static Editor findEditor(@NotNull Project project, @NotNull PlaceInfo changePlace) {
    try {
      JComponent component = FileEditorManagerEx.getInstanceEx(project).getPreferredFocusedComponent();
      if (component == null || !component.isShowing() || !(component instanceof EditorComponentImpl)) {
        return null;
      }
    }
    catch (Exception e) {
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
                                                                      @NotNull LogicalPosition logicalPosition) {
    FileBreadcrumbsCollector collector = FileBreadcrumbsCollector.findBreadcrumbsCollector(project, changePlace.getFile());
    Collection<Iterable<? extends Crumb>> result = ContainerUtil.emptyList();
    if (collector != null) {
      CollectConsumer<Iterable<? extends Crumb>> consumer = new CollectConsumer<>();
      collector.updateCrumbs(changePlace.getFile(),
                             editor,
                             editor.logicalPositionToOffset(logicalPosition),
                             new ProgressIndicatorBase(),
                             consumer);
      result = consumer.getResult();
    }
    return result;
  }

  @NotNull
  private Map<PlaceInfo, PlaceInfoPersistentItem> getMap(boolean showChanged) {
    return showChanged ? myChangedItems : myRecentItems;
  }

  private static class PlaceInfoPersistentItem {
    @NotNull private final Collection<Iterable<? extends Crumb>> myCrumbs;
    @NotNull private final RangeMarker myPositionOffset;
    @NotNull private final EditorColorsScheme myScheme;

    PlaceInfoPersistentItem(@NotNull Collection<Iterable<? extends Crumb>> crumbs,
                            @NotNull RangeMarker positionOffset,
                            @NotNull EditorColorsScheme scheme) {
      myCrumbs = crumbs;
      myPositionOffset = positionOffset;
      myScheme = scheme;
    }

    @NotNull
    private EditorColorsScheme getScheme() {
      return myScheme;
    }

    @NotNull
    private Collection<Iterable<? extends Crumb>> getCrumbs() {
      return myCrumbs;
    }

    @NotNull
    private RangeMarker getPositionOffset() {
      return myPositionOffset;
    }
  }

  private static class RecentLocationFixedSizeHashMap extends LinkedHashMap<PlaceInfo, PlaceInfoPersistentItem> {
    @Override
    protected boolean removeEldestEntry(Map.Entry<PlaceInfo, PlaceInfoPersistentItem> eldest) {
      return size() > UISettings.getInstance().getRecentLocationsLimit();
    }
  }
}
