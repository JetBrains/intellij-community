// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;

import java.awt.*;

public class PlatformCoreDataKeys extends CommonDataKeys {
  public static final DataKey<VirtualFile> PROJECT_FILE_DIRECTORY = DataKey.create("context.ProjectFileDirectory");

  public static final DataKey<Module> MODULE = DataKey.create("module");

  public static final DataKey<FileEditor> FILE_EDITOR = DataKey.create("fileEditor");

  /**
   * Returns the text of currently selected file/file revision
   */
  public static final DataKey<String> FILE_TEXT = DataKey.create("fileText");

  /**
   * Returns {@link Boolean#TRUE} if action is executed in modal context and
   * {@link Boolean#FALSE} if action is executed not in modal context. If context
   * is unknown returns {@code null}.
   */
  public static final DataKey<Boolean> IS_MODAL_CONTEXT = DataKey.create("isModalContext");

  /**
   * Returns help id.
   *
   * @see HelpManager#invokeHelp(String)
   */
  public static final DataKey<String> HELP_ID = DataKey.create("helpId");

  /**
   * Returns project if project node is selected (in project view)
   */
  public static final DataKey<Project> PROJECT_CONTEXT = DataKey.create("context.Project");

  /**
   * Returns {@link Component} currently in focus, DataContext should be retrieved for.
   */
  public static final DataKey<Component> CONTEXT_COMPONENT = DataKey.create("contextComponent");

  /**
   * Use this key to split a data provider into two parts: the quick part to be queried on EDT,
   * and the slow part to be queried on a background thread or under a progress.
   * That slow part shall be returned when this data key is requested.
   */
  public static final DataKey<Iterable<DataProvider>> SLOW_DATA_PROVIDERS = DataKey.create("slowDataProviders");
  /**
   * Returns single selection item.
   *
   * @see #SELECTED_ITEMS
   */
  public static final DataKey<Object> SELECTED_ITEM = DataKey.create("selectedItem");
  /**
   * Returns multi selection items.
   *
   * @see #SELECTED_ITEM
   */
  public static final DataKey<Object[]> SELECTED_ITEMS = DataKey.create("selectedItems");

  public static final DataKey<PsiElement[]> PSI_ELEMENT_ARRAY = DataKey.create("psi.Element.array");
}
