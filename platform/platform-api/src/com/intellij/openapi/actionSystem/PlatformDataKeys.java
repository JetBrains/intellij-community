/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.actionSystem;

import com.intellij.ide.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diff.DiffViewer;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.ContentManager;

import java.awt.*;
import java.util.Comparator;

/**
 * @author yole
 */
public class PlatformDataKeys extends CommonDataKeys {
  public static final DataKey<FileEditor> FILE_EDITOR = DataKey.create("fileEditor");

  /**
   *  Returns the text of currently selected file/file revision
   */
  public static final DataKey<String> FILE_TEXT = DataKey.create("fileText");

  /**
   * Returns Boolean.TRUE if action is executed in modal context and
   * Boolean.FALSE if action is executed not in modal context. If context
   * is unknown then the value of this data constant is {@code null}.
   */
  public static final DataKey<Boolean> IS_MODAL_CONTEXT = DataKey.create("isModalContext");
  public static final DataKey<DiffViewer> DIFF_VIEWER = DataKey.create("diffViewer");
  public static final DataKey<DiffViewer> COMPOSITE_DIFF_VIEWER = DataKey.create("compositeDiffViewer");

  /**
   * Returns help id (String)
   */
  public static final DataKey<String> HELP_ID = DataKey.create("helpId");

  /**
   * Returns project if project node is selected (in project view)
   */
  public static final DataKey<Project> PROJECT_CONTEXT = DataKey.create("context.Project");

  /**
   * Returns java.awt.Component currently in focus, DataContext should be retrieved for
   */
  public static final DataKey<Component> CONTEXT_COMPONENT = DataKey.create("contextComponent");
  public static final DataKey<CopyProvider> COPY_PROVIDER = DataKey.create("copyProvider");
  public static final DataKey<CutProvider> CUT_PROVIDER = DataKey.create("cutProvider");
  public static final DataKey<PasteProvider> PASTE_PROVIDER = DataKey.create("pasteProvider");
  public static final DataKey<DeleteProvider> DELETE_ELEMENT_PROVIDER = DataKey.create("deleteElementProvider");
  public static final DataKey<Object> SELECTED_ITEM = DataKey.create("selectedItem");
  public static final DataKey<Object[]> SELECTED_ITEMS = DataKey.create("selectedItems");
  public static final DataKey<Rectangle> DOMINANT_HINT_AREA_RECTANGLE = DataKey.create("dominant.hint.rectangle");
  public static final DataKey<ContentManager> CONTENT_MANAGER = DataKey.create("contentManager");
  public static final DataKey<ToolWindow> TOOL_WINDOW = DataKey.create("TOOL_WINDOW");
  public static final DataKey<TreeExpander> TREE_EXPANDER = DataKey.create("treeExpander");
  public static final DataKey<ExporterToTextFile> EXPORTER_TO_TEXT_FILE = DataKey.create("exporterToTextFile");
  public static final DataKey<VirtualFile> PROJECT_FILE_DIRECTORY = DataKey.create("context.ProjectFileDirectory");
  public static final DataKey<Disposable> UI_DISPOSABLE = DataKey.create("ui.disposable");

  public static final DataKey<ContentManager> NONEMPTY_CONTENT_MANAGER = DataKey.create("nonemptyContentManager");
  public static final DataKey<ModalityState> MODALITY_STATE = DataKey.create("ModalityState");
  public static final DataKey<Boolean> SOURCE_NAVIGATION_LOCKED = DataKey.create("sourceNavigationLocked");

  public static final DataKey<String> PREDEFINED_TEXT = DataKey.create("predefined.text.value");

  public static final DataKey<String> SEARCH_INPUT_TEXT = DataKey.create("search.input.text.value");
  public static final DataKey<Object> SPEED_SEARCH_COMPONENT = DataKey.create("speed.search.component.value");

  /**
   * Returns java.awt.Point to guess where to show context menu invoked by key.
   * This point should be relative to the currently focused component
   */
  public static final DataKey<Point> CONTEXT_MENU_POINT = DataKey.create("contextMenuPoint");

  /**
   * It's allowed to assign multiple actions to the same keyboard shortcut. Actions system filters them on the current
   * context basis during processing (e.g. we can have two actions assigned to the same shortcut but one of them is
   * configured to be inapplicable in modal dialog context).
   * <p/>
   * However, there is a possible case that there is still more than one action applicable for particular keyboard shortcut
   * after filtering. The first one is executed then. Hence, actions processing order becomes very important.
   * <p/>
   * Current key allows to specify custom actions sorter to use if any. I.e. every component can define it's custom
   * sorting rule in order to define priorities for target actions (classes of actions).
   *
   * @deprecated use com.intellij.openapi.actionSystem.ActionPromoter
   */
  @Deprecated
  public static final DataKey<Comparator<? super AnAction>> ACTIONS_SORTER = DataKey.create("actionsSorter");
}
