package com.intellij.openapi.actionSystem.ex;

import com.intellij.openapi.actionSystem.DataConstants;
import org.jetbrains.annotations.NonNls;

public interface DataConstantsEx extends DataConstants {
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

  /**
   * Returns {@link com.intellij.execution.configurations.RuntimeConfiguration}
   */
  @NonNls String RUNTIME_CONFIGURATION = "runtimeConfiguration";

  /**
   * Returns {@link com.intellij.psi.PsiElement}
   * */
  @NonNls String SECONDARY_PSI_ELEMENT = "secondaryPsiElement";

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
   * returns array of {@link com.intellij.uiDesigner.projectView.Form}
   */
  @NonNls String GUI_DESIGNER_FORM_ARRAY = "form.array";

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
}
