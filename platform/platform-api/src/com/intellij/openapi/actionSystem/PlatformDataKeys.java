// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.ide.*;
import com.intellij.ide.ui.PopupLocator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.ApiStatus;

import java.awt.*;
import java.util.Comparator;

public class PlatformDataKeys extends PlatformCoreDataKeys {

  public static final DataKey<CopyProvider> COPY_PROVIDER = DataKey.create("copyProvider");
  public static final DataKey<CutProvider> CUT_PROVIDER = DataKey.create("cutProvider");
  public static final DataKey<PasteProvider> PASTE_PROVIDER = DataKey.create("pasteProvider");
  public static final DataKey<DeleteProvider> DELETE_ELEMENT_PROVIDER = DataKey.create("deleteElementProvider");

  public static final DataKey<Rectangle> DOMINANT_HINT_AREA_RECTANGLE = DataKey.create("dominant.hint.rectangle");

  public static final DataKey<ContentManager> CONTENT_MANAGER = DataKey.create("contentManager");
  public static final DataKey<ContentManager> NONEMPTY_CONTENT_MANAGER = DataKey.create("nonemptyContentManager");

  public static final DataKey<ToolWindow> TOOL_WINDOW = DataKey.create("TOOL_WINDOW");
  public static final DataKey<ToolWindow[]> LAST_ACTIVE_TOOL_WINDOWS = DataKey.create("LAST_ACTIVE_TOOL_WINDOWS");

  public static final DataKey<StatusBar> STATUS_BAR = DataKey.create("STATUS_BAR");

  public static final DataKey<TreeExpander> TREE_EXPANDER = DataKey.create("treeExpander");

  /**
   * @see com.intellij.ide.actions.ExportToTextFileAction
   */
  public static final DataKey<ExporterToTextFile> EXPORTER_TO_TEXT_FILE = DataKey.create("exporterToTextFile");

  public static final DataKey<Disposable> UI_DISPOSABLE = DataKey.create("ui.disposable");

  public static final DataKey<ModalityState> MODALITY_STATE = DataKey.create("ModalityState");

  public static final DataKey<Boolean> SOURCE_NAVIGATION_LOCKED = DataKey.create("sourceNavigationLocked");

  public static final DataKey<String> PREDEFINED_TEXT = DataKey.create("predefined.text.value");

  public static final DataKey<String> SEARCH_INPUT_TEXT = DataKey.create("search.input.text.value");
  public static final DataKey<Object> SPEED_SEARCH_COMPONENT = DataKey.create("speed.search.component.value");

  /**
   * Returns {@link Point} to guess where to show context menu invoked by key.
   * <p/>
   * This point should be relative to the currently focused component
   */
  public static final DataKey<Point> CONTEXT_MENU_POINT = DataKey.create("contextMenuPoint");

  /**
   * Allows more precise positioning than {@link PlatformDataKeys#CONTEXT_MENU_POINT} by using information
   * about popup, e.g. its size.
   */
  @ApiStatus.Experimental
  public static final DataKey<PopupLocator> CONTEXT_MENU_LOCATOR = DataKey.create("contextMenuLocator");

  /**
   * It's allowed to assign multiple actions to the same keyboard shortcut. Actions system filters them on the current
   * context basis during processing (e.g., we can have two actions assigned to the same shortcut, but one of them is
   * configured to be inapplicable in modal dialog context).
   * <p/>
   * However, there is a possible case that there is still more than one action applicable for particular keyboard shortcut
   * after filtering. The first one is executed then. Hence, action processing order becomes very important.
   * <p/>
   * Current key allows specifying custom action sorter to use if any. I.e., every component can define its custom
   * sorting rule to define priorities for target actions (classes of actions).
   *
   * @deprecated use {@link ActionPromoter}
   */
  @Deprecated(forRemoval = true)
  public static final DataKey<Comparator<? super AnAction>> ACTIONS_SORTER = DataKey.create("actionsSorter");
}
