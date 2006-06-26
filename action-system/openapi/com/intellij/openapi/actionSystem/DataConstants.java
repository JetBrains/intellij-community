/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
 */

@SuppressWarnings({"HardCodedStringLiteral"})
public interface DataConstants {
  /**
   * Returns {@link com.intellij.openapi.project.Project}
   */
  String PROJECT = "project";

  /**
   * Returns {@link com.intellij.openapi.module.Module}
   */
  String MODULE = "module";

  /**
   * Returns {@link com.intellij.openapi.vfs.VirtualFile}
   */
  String VIRTUAL_FILE = "virtualFile";

  /**
   * Returns array of {@link com.intellij.openapi.vfs.VirtualFile}
   */
  String VIRTUAL_FILE_ARRAY = "virtualFileArray";

  /**
   * Returns {@link com.intellij.openapi.editor.Editor}
   */
  String EDITOR = "editor";

  /**
   * Returns {@link com.intellij.openapi.fileEditor.FileEditor}
   */
  String FILE_EDITOR = "fileEditor";

  /**
   * Returns {@link com.intellij.openapi.fileEditor.OpenFileDescriptor}
   * @deprecated {@link #NAVIGATABLE} should be used instead
   */
  String OPEN_FILE_DESCRIPTOR = "openFileDescriptor";

  /**
   * Returns the text of currently selected file/file revision
   */
  String FILE_TEXT = "fileText";

  /**
   * Returns Boolean.TRUE if action is executed in modal context and
   * Boolean.FALSE if action is executed not in modal context. If context
   * is unknown then the value of this data constant is <code>null</code>.
   */
  String IS_MODAL_CONTEXT = "isModalContext";

  /**
   * Returns {@link com.intellij.openapi.diff.DiffViewer}
   */
  String DIFF_VIEWER = "diffViewer";
  /**
   * Returns help id (String)
   */
  String HELP_ID = "helpId";
  /**
   * Returns project if project node is selected (in project view)
   */
  String PROJECT_CONTEXT = "context.Project";
  /**
   * Returns module if module node is selected (in module view)
   */
  String MODULE_CONTEXT = "context.Module";
  String MODULE_CONTEXT_ARRAY = "context.Module.Array";

  /**
   * Returns {@link com.intellij.pom.Navigatable}
   */
  String NAVIGATABLE = "Navigatable";

  /**
   * Returns an array of {@link com.intellij.pom.Navigatable}
   */
  String NAVIGATABLE_ARRAY = "NavigatableArray";

  /**
   * Returns {@link com.intellij.ide.ExporterToTextFile}
   */

  String EXPORTER_TO_TEXT_FILE = "exporterToTextFile";

  /**
   * Returns {@link com.intellij.psi.PsiElement}
   */
  String PSI_ELEMENT = "psi.Element";

  /**
   * Returns {@link com.intellij.psi.PsiFile}
   */
  String PSI_FILE = "psi.File";

  /**
   * Returns {@link com.intellij.lang.Language}
   */
  String LANGUAGE = "Language";

  /**
   * Returns java.awt.Component currently in focus, DataContext should be retreived for
   */
  @NonNls String CONTEXT_COMPONENT = "contextComponent";

  /**
   * Returns {@link com.intellij.ide.IdeView} (one of project, packages, commander or favorites view).
   *
   * @since 5.1
   */
  @NonNls String IDE_VIEW = "IDEView";

  /**
   * Returns array of selected {@link com.intellij.openapi.vcs.changes.ChangeList}s.
   * @since 6.0
   */
  @NonNls String CHANGE_LISTS = "vcs.ChangeList";

  /**
   * Returns array of selected {@link com.intellij.openapi.vcs.changes.Change}s.
   * @since 6.0
   */
  @NonNls String CHANGES = "vcs.Change";

  /**
   * Returns com.intellij.psi.PsiElement[]
   */
  @NonNls String PSI_ELEMENT_ARRAY = "psi.Element.array";
  /**
   * Returns com.intellij.ide.CopyProvider
   */
  @NonNls String COPY_PROVIDER = "copyProvider";
  /**
   * Returns com.intellij.ide.CutProvider
   */
  @NonNls String CUT_PROVIDER = "cutProvider";
  /**
   * Returns com.intellij.ide.PasteProvider
   */
  @NonNls String PASTE_PROVIDER = "pasteProvider";
  /**
   * Returns com.intellij.ide.DeleteProvider
   */
  @NonNls String DELETE_ELEMENT_PROVIDER = "deleteElementProvider";
}
