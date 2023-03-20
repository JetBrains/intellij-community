// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.SlowOperations;

import java.awt.*;

/**
 * @see CommonDataKeys
 * @see com.intellij.openapi.actionSystem.LangDataKeys
 * @see com.intellij.openapi.actionSystem.PlatformDataKeys
 */
public class PlatformCoreDataKeys extends CommonDataKeys {
  public static final DataKey<VirtualFile> PROJECT_FILE_DIRECTORY = DataKey.create("context.ProjectFileDirectory");

  public static final DataKey<Module> MODULE = DataKey.create("module");

  public static final DataKey<FileEditor> FILE_EDITOR = DataKey.create("fileEditor");

  /**
   * Returns the text of currently selected file/file revision
   * @deprecated Use what's needed from {@link com.intellij.ide.impl.dataRules.FileTextRule}
   */
  @Deprecated(forRemoval = true)
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
   * @see com.intellij.openapi.help.HelpManager#invokeHelp(String)
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
   * A key to use to split a data provider into fast EDT and potentially slow BGT parts,
   * and to avoid the {@link SlowOperations#assertSlowOperationsAreAllowed()} assertion.
   * <p/>
   * Also, it is now mandatory to provide PSI only on BGT, i.e. an assertion is now triggered on data validation.
   * <p/>
   * The general approach is as follows:
   * <code><pre>
   * public @Nullable Object getData(@NotNull String dataId) {
   *   // called on EDT, no slow operations are allowed
   *   if (BGT_DATA_PROVIDER.is(dataId)) {
   *     var selection = // very important to capture the UI selection now
   *     return (DataProvider)slowId -> getSlowData(slowId, selection);
   *   }
   *   ...
   * }
   *
   * private static @Nullable Object getSlowData(@NotNull String dataId, selection) {
   *   // called on BGT, no unsafe Swing state access is allowed
   *   if (PSI_ELEMENT.is(dataId)) {
   *     // extract PSI from selection and return it
   *   }
   *   ...
   * }
   * </pre></code>
   *
   * @see SlowOperations#assertSlowOperationsAreAllowed()
   */
  public static final DataKey<DataProvider> BGT_DATA_PROVIDER = DataKey.create("bgtDataProvider");

  /** @deprecated Use {@link #BGT_DATA_PROVIDER} and {@link CompositeDataProvider} */
  @Deprecated
  public static final DataKey<Iterable<DataProvider>> SLOW_DATA_PROVIDERS = DataKey.create("slowDataProviders");

  /**
   * Returns single UI selection item.
   *
   * @see #SELECTED_ITEMS
   */
  public static final DataKey<Object> SELECTED_ITEM = DataKey.create("selectedItem");

  /**
   * Returns multiple UI selection items.
   *
   * @see #SELECTED_ITEM
   */
  public static final DataKey<Object[]> SELECTED_ITEMS = DataKey.create("selectedItems");

  public static final DataKey<PsiElement[]> PSI_ELEMENT_ARRAY = DataKey.create("psi.Element.array");
}
