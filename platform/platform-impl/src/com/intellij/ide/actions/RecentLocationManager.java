// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite;
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl;
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl.PlaceInfo;
import com.intellij.openapi.fileEditor.impl.text.TextEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
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
    subscribeOnEditorClose(connection);
  }

  private void subscribeOnEditorClose(@NotNull MessageBusConnection connection) {
    connection.subscribe(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER, new FileEditorManagerListener.Before() {
      @Override
      public void beforeFileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        FileEditor fileEditor = source.getSelectedEditor(file);
        Editor editor = getEditor(fileEditor);
        if (editor == null) {
          return;
        }

        FileEditorManagerEx editorManagerEx = FileEditorManagerEx.getInstanceEx(myProject);
        FileEditorWithProvider selectedEditorWithProvider = editorManagerEx.getSelectedEditorWithProvider(file);

        if (selectedEditorWithProvider == null) {
          return;
        }

        PlaceInfo placeInfo = new PlaceInfo(file,
                                            fileEditor.getState(FileEditorStateLevel.NAVIGATION),
                                            selectedEditorWithProvider.getProvider().getEditorTypeId(),
                                            editorManagerEx.getCurrentWindow());

        int offset = editor.getCaretModel().getOffset();
        PlaceInfoPersistentItem placeInfoPersistentItem =
          new PlaceInfoPersistentItem(editor.getDocument().createRangeMarker(offset, offset), editor.getColorsScheme());

        myRecentItems.put(placeInfo, placeInfoPersistentItem);
      }
    });
  }

  @Nullable
  RangeMarker getPositionOffset(@NotNull PlaceInfo placeInfo, boolean showChanged) {
    PlaceInfoPersistentItem item = getMap(showChanged).get(placeInfo);
    return item == null ? null : item.getPositionOffsetMarker();
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
        Editor editor = findEditor(myProject, changePlace);
        if (editor == null) {
          myRecentItems
            .keySet()
            .stream()
            .filter(info -> arePlacesEqual(changePlace, info))
            .findFirst()
            .ifPresent(placeInfo -> {
              //correct old key
              PlaceInfoPersistentItem item = myRecentItems.remove(placeInfo);
              myRecentItems.put(changePlace, item);
            });

          return;
        }

        LogicalPosition logicalPosition = getLogicalPosition(changePlace);
        if (logicalPosition == null) {
          return;
        }

        int offset = editor.logicalPositionToOffset(logicalPosition);
        getItems(isChanged).put(changePlace, new PlaceInfoPersistentItem(editor.getDocument().createRangeMarker(offset, offset),
                                                                                   editor.getColorsScheme()));
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

  private static boolean arePlacesEqual(@NotNull PlaceInfo info1, @NotNull PlaceInfo info2) {
    return info2.getFile().equals(info1.getFile()) &&
           info2.getEditorTypeId().equals(info1.getEditorTypeId())
           && info2.getNavigationState().equals(info1.getNavigationState()) &&
           info2.getWindow().equals(info1.getWindow());
  }

  private static void removePlace(@NotNull PlaceInfo placeToRemove, @NotNull Map<PlaceInfo, PlaceInfoPersistentItem> items) {
    items.remove(placeToRemove);
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

    return getEditor(composite.getSelectedWithProvider().getFileEditor());
  }

  @Nullable
  private static Editor getEditor(@Nullable FileEditor fileEditor) {
    return !(fileEditor instanceof TextEditor) ? null : ((TextEditor)fileEditor).getEditor();
  }

  @NotNull
  private Map<PlaceInfo, PlaceInfoPersistentItem> getMap(boolean showChanged) {
    return showChanged ? myChangedItems : myRecentItems;
  }

  private static class PlaceInfoPersistentItem {
    @NotNull private final RangeMarker myPositionOffsetMarker;
    @NotNull private final EditorColorsScheme myScheme;

    PlaceInfoPersistentItem(@NotNull RangeMarker positionOffsetMarker, @NotNull EditorColorsScheme scheme) {
      myPositionOffsetMarker = positionOffsetMarker;
      myScheme = scheme;
    }

    @NotNull
    private EditorColorsScheme getScheme() {
      return myScheme;
    }

    @NotNull
    private RangeMarker getPositionOffsetMarker() {
      return myPositionOffsetMarker;
    }
  }

  private static class RecentLocationFixedSizeHashMap extends LinkedHashMap<PlaceInfo, PlaceInfoPersistentItem> {
    @Override
    protected boolean removeEldestEntry(Map.Entry<PlaceInfo, PlaceInfoPersistentItem> eldest) {
      return size() > UISettings.getInstance().getRecentLocationsLimit();
    }
  }
}
