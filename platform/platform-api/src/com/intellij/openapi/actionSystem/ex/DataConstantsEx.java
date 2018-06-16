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

package com.intellij.openapi.actionSystem.ex;

import org.jetbrains.annotations.NonNls;


/**
 * Identifiers for data items which can be returned from {@link com.intellij.openapi.actionSystem.DataContext#getData(String)} and
 * {@link com.intellij.openapi.actionSystem.DataProvider#getData(String)}.
 * @deprecated {@link DataKeys} and {@link com.intellij.openapi.actionSystem.DataKey#getData} should be used instead
 */
@Deprecated
@SuppressWarnings({"HardCodedStringLiteral", "JavadocReference"})
public interface DataConstantsEx {
  /**
   * Returns {@link com.intellij.psi.PsiElement}
   */
  @NonNls String TARGET_PSI_ELEMENT = "psi.TargetElement";
  /**
   * Returns {@link com.intellij.psi.PsiElement}
   */
  @NonNls String PASTE_TARGET_PSI_ELEMENT = "psi.pasteTargetElement";
  /**
   * Returns {@link com.intellij.usages.UsageView}
   */
  @NonNls String USAGE_VIEW = "usageView";
  /**
   * Returns {@link com.intellij.codeInspection.ui.InspectionResultsView}
   */
  @NonNls String INSPECTION_VIEW = "inspectionView";
  /**
   * Returns {@link com.intellij.ide.TreeExpander}
   */
  @NonNls String TREE_EXPANDER = "treeExpander";
  /**
   * Returns {@link com.intellij.ui.content.ContentManager}
   */
  @NonNls String CONTENT_MANAGER = "contentManager";

  @NonNls String NONEMPTY_CONTENT_MANAGER = "nonemptyContentManager";

  /**
   * Returns {@link com.intellij.execution.configurations.RuntimeConfiguration}
   */
  @NonNls String RUNTIME_CONFIGURATION = "runtimeConfiguration";

  /**
   * Returns project file directory as {@link com.intellij.openapi.vfs.VirtualFile}
   */
  @NonNls String PROJECT_FILE_DIRECTORY = "context.ProjectFileDirectory";

  /**
   * returns {@link com.intellij.openapi.application.ModalityState}
   */
  @NonNls String MODALITY_STATE = "ModalityState";

  /**
   * returns Boolean
   */
  @NonNls String SOURCE_NAVIGATION_LOCKED = "sourceNavigationLocked";

  /**
   * returns array of {@link com.intellij.ide.projectView.impl.ModuleGroup}
   */
  @NonNls String MODULE_GROUP_ARRAY = "moduleGroup.array";

  /**
   * returns array of {@link com.intellij.ide.projectView.impl.nodes.LibraryGroupElement}
   */
  @NonNls String LIBRARY_GROUP_ARRAY = "libraryGroup.array";

  /**
   * returns array of {@link com.intellij.ide.projectView.impl.nodes.NamedLibraryElement}
   */
  @NonNls String NAMED_LIBRARY_ARRAY = "namedLibrary.array";

  /**
   * returns {@link com.intellij.openapi.fileEditor.impl.EditorWindow}
   */
  @NonNls String EDITOR_WINDOW = "editorWindow";

  /**
   * returns array of {@link com.intellij.lang.properties.ResourceBundle}
   */
  @NonNls String RESOURCE_BUNDLE_ARRAY = "resource.bundle.array";


  /**
   * returns {@link com.intellij.openapi.module.ModifiableModuleModel}
   */
  @NonNls String MODIFIABLE_MODULE_MODEL = "modifiable.module.model";

  /**
   * returns {@link com.intellij.ide.projectView.impl.nodes.PackageElement}
   */
  @NonNls String PACKAGE_ELEMENT = "package.element";
}
