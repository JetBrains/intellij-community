/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import org.jetbrains.annotations.NonNls;

/**
 * Identifiers for data items which can be returned from {@link DataContext#getData(String)} and
 * {@link DataProvider#getData(String)}.
 *
 * @deprecated {@link DataKeys} and {@link DataKey#getData} should be used instead
 */
@SuppressWarnings({"HardCodedStringLiteral", "JavadocReference"})
public interface DataConstants {
  /**
   * Returns {@link com.intellij.openapi.project.Project}
   *
   * @deprecated use {@link PlatformDataKeys#PROJECT} instead
   */
  String PROJECT = CommonDataKeys.PROJECT.getName();

  /**
   * Returns {@link com.intellij.openapi.module.Module}
   *
   * @deprecated use {@link com.intellij.openapi.actionSystem.LangDataKeys#MODULE} instead
   */
  @NonNls String MODULE = "module";

  /**
   * Returns {@link com.intellij.openapi.vfs.VirtualFile}
   *
   * @deprecated use {@link PlatformDataKeys#VIRTUAL_FILE} instead
   */
  String VIRTUAL_FILE = CommonDataKeys.VIRTUAL_FILE.getName();

  /**
   * Returns array of {@link com.intellij.openapi.vfs.VirtualFile}
   *
   * @deprecated use {@link PlatformDataKeys#VIRTUAL_FILE_ARRAY} instead
   */
  String VIRTUAL_FILE_ARRAY = CommonDataKeys.VIRTUAL_FILE_ARRAY.getName();

  /**
   * Returns {@link com.intellij.openapi.editor.Editor}
   *
   * @deprecated use {@link PlatformDataKeys#EDITOR} instead
   */
  String EDITOR = CommonDataKeys.EDITOR.getName();

  /**
   * Returns {@link com.intellij.openapi.fileEditor.FileEditor}
   *
   * @deprecated use {@link PlatformDataKeys#FILE_EDITOR} instead
   */
  String FILE_EDITOR = PlatformDataKeys.FILE_EDITOR.getName();

  /**
   * Returns {@link com.intellij.openapi.fileEditor.OpenFileDescriptor}
   *
   * @deprecated {@link PlatformDataKeys#NAVIGATABLE} should be used instead
   */
  @NonNls String OPEN_FILE_DESCRIPTOR = "openFileDescriptor";

  /**
   * Returns the text of currently selected file/file revision
   *
   * @deprecated use {@link PlatformDataKeys#FILE_TEXT} instead
   */
  String FILE_TEXT = PlatformDataKeys.FILE_TEXT.getName();

  /**
   * Returns Boolean.TRUE if action is executed in modal context and
   * Boolean.FALSE if action is executed not in modal context. If context
   * is unknown then the value of this data constant is {@code null}.
   *
   * @deprecated use {@link PlatformDataKeys#IS_MODAL_CONTEXT} instead
   */
  String IS_MODAL_CONTEXT = PlatformDataKeys.IS_MODAL_CONTEXT.getName();

  /**
   * Returns {@link com.intellij.openapi.diff.DiffViewer}
   *
   * @deprecated use {@link PlatformDataKeys#DIFF_VIEWER} instead
   */
  String DIFF_VIEWER = PlatformDataKeys.DIFF_VIEWER.getName();

  /**
   * Returns help id (String)
   *
   * @deprecated use {@link PlatformDataKeys#HELP_ID} instead
   */
  String HELP_ID = PlatformDataKeys.HELP_ID.getName();

  /**
   * Returns project if project node is selected (in project view)
   *
   * @deprecated use {@link PlatformDataKeys#PROJECT_CONTEXT} instead
   */
  String PROJECT_CONTEXT = PlatformDataKeys.PROJECT_CONTEXT.getName();

  /**
   * Returns module if module node is selected (in module view)
   *
   * @deprecated use {@link com.intellij.openapi.actionSystem.LangDataKeys#MODULE_CONTEXT} instead
   */
  @NonNls String MODULE_CONTEXT = "context.Module";

  /**
   * @deprecated use {@link com.intellij.openapi.actionSystem.LangDataKeys#MODULE_CONTEXT_ARRAY} instead
   */
  @NonNls String MODULE_CONTEXT_ARRAY = "context.Module.Array";

  /**
   * Returns {@link com.intellij.pom.Navigatable}
   *
   * @deprecated use {@link PlatformDataKeys#NAVIGATABLE} instead
   */
  String NAVIGATABLE = CommonDataKeys.NAVIGATABLE.getName();

  /**
   * Returns an array of {@link com.intellij.pom.Navigatable}
   *
   * @deprecated use {@link PlatformDataKeys#NAVIGATABLE_ARRAY} instead
   */
  String NAVIGATABLE_ARRAY = CommonDataKeys.NAVIGATABLE_ARRAY.getName();

  /**
   * Returns {@link com.intellij.ide.ExporterToTextFile}
   *
   * @deprecated use {@link PlatformDataKeys#EXPORTER_TO_TEXT_FILE} instead
   */
  String EXPORTER_TO_TEXT_FILE = PlatformDataKeys.EXPORTER_TO_TEXT_FILE.getName();

  /**
   * Returns {@link com.intellij.psi.PsiElement}
   *
   * @deprecated use {@link CommonDataKeys#PSI_ELEMENT} instead
   */
  @NonNls String PSI_ELEMENT = "psi.Element";

  /**
   * Returns {@link com.intellij.psi.PsiFile}
   *
   * @deprecated use {@link CommonDataKeys#PSI_FILE} instead
   */
  @NonNls String PSI_FILE = "psi.File";

  /**
   * Returns {@link com.intellij.lang.Language}
   *
   * @deprecated use {@link com.intellij.openapi.actionSystem.LangDataKeys#LANGUAGE} instead
   */
  @NonNls String LANGUAGE = "Language";

  /**
   * Returns java.awt.Component currently in focus, DataContext should be retrieved for
   *
   * @deprecated use {@link PlatformDataKeys#CONTEXT_COMPONENT} instead
   */
  String CONTEXT_COMPONENT = PlatformDataKeys.CONTEXT_COMPONENT.getName();

  /**
   * Returns {@link com.intellij.ide.IdeView} (one of project, packages, commander or favorites view).
   *
   * @since 5.1
   * @deprecated use {@link com.intellij.openapi.actionSystem.LangDataKeys#IDE_VIEW} instead
   */
  @NonNls String IDE_VIEW = "IDEView";

  /**
   * Returns array of selected {@link com.intellij.openapi.vcs.changes.ChangeList}s.
   *
   * @since 6.0
   * @deprecated use {@link com.intellij.openapi.vcs.VcsDataKeys#CHANGE_LISTS} instead
   */
  @NonNls String CHANGE_LISTS = "vcs.ChangeList";

  /**
   * Returns array of selected {@link com.intellij.openapi.vcs.changes.Change}s.
   *
   * @since 6.0
   * @deprecated use {@link com.intellij.openapi.vcs.VcsDataKeys#CHANGES} instead
   */
  @NonNls String CHANGES = "vcs.Change";

  /**
   * Returns com.intellij.psi.PsiElement[]
   *
   * @deprecated use {@link com.intellij.openapi.actionSystem.LangDataKeys#PSI_ELEMENT_ARRAY} instead
   */
  @NonNls String PSI_ELEMENT_ARRAY = "psi.Element.array";

  /**
   * Returns com.intellij.ide.CopyProvider
   *
   * @deprecated use {@link PlatformDataKeys#COPY_PROVIDER} instead
   */
  String COPY_PROVIDER = PlatformDataKeys.COPY_PROVIDER.getName();

  /**
   * Returns com.intellij.ide.CutProvider
   *
   * @deprecated use {@link PlatformDataKeys#CUT_PROVIDER} instead
   */
  String CUT_PROVIDER = PlatformDataKeys.CUT_PROVIDER.getName();

  /**
   * Returns com.intellij.ide.PasteProvider
   *
   * @deprecated use {@link PlatformDataKeys#PASTE_PROVIDER} instead
   */
  String PASTE_PROVIDER = PlatformDataKeys.PASTE_PROVIDER.getName();

  /**
   * Returns com.intellij.ide.DeleteProvider
   *
   * @deprecated use {@link PlatformDataKeys#DELETE_ELEMENT_PROVIDER} instead
   */
  String DELETE_ELEMENT_PROVIDER = PlatformDataKeys.DELETE_ELEMENT_PROVIDER.getName();

  /**
   * Returns com.intellij.openapi.editor.Editor even if focuses currently is in find bar
   *
   * @deprecated use {@link PlatformDataKeys#EDITOR} instead
   */
  String EDITOR_EVEN_IF_INACTIVE = CommonDataKeys.EDITOR_EVEN_IF_INACTIVE.getName();

  /**
   * @deprecated use {@link PlatformDataKeys#SELECTED_ITEM} instead
   */
  String SELECTED_ITEM = PlatformDataKeys.SELECTED_ITEM.getName();

  /**
   * @deprecated use {@link PlatformDataKeys#DOMINANT_HINT_AREA_RECTANGLE} instead
   */
  String DOMINANT_HINT_AREA_RECTANGLE = PlatformDataKeys.DOMINANT_HINT_AREA_RECTANGLE.getName();
}
