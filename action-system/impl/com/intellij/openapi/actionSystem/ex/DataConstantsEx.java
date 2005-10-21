package com.intellij.openapi.actionSystem.ex;

import com.intellij.openapi.actionSystem.DataConstants;
import org.jetbrains.annotations.NonNls;

public interface DataConstantsEx extends DataConstants {
  /**
   * Returns com.intellij.psi.PsiElement
   */
  @NonNls String TARGET_PSI_ELEMENT = "psi.TargetElement";
  /**
   * Returns com.intellij.psi.PsiElement
   */
  @NonNls String PASTE_TARGET_PSI_ELEMENT = "psi.pasteTargetElement";
  /**
   * Returns com.intellij.usageView.UsageView
   */
  @NonNls String USAGE_VIEW = "usageView";
  /**
   * Returns com.intellij.codeInspection.ui.InsepctionResultsView
   */
  @NonNls String INSPECTION_VIEW = "inspectionView";
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
   * Returns com.intellij.ide.IdeView
   */
  @NonNls String IDE_VIEW = "IDEView";
  /**
   * Returns com.intellij.ide.DeleteProvider
   */
  @NonNls String DELETE_ELEMENT_PROVIDER = "deleteElementProvider";
  /**
   * Returns TreeExpander
   */
  @NonNls String TREE_EXPANDER = "treeExpander";
  /**
   * Returns ContentManager
   */
  @NonNls String CONTENT_MANAGER = "contentManager";

  /**
   * Returns java.awt.Component
   */
  @NonNls String CONTEXT_COMPONENT = "contextComponent";
  /**
   * Returns RuntimeConfiguration
   */
  @NonNls String RUNTIME_CONFIGURATION = "runtimeConfiguration";

  /** Returns PsiElement */
  @NonNls String SECONDARY_PSI_ELEMENT = "secondaryPsiElement";

  /**
   * Returns project file directory
   */
  @NonNls String PROJECT_FILE_DIRECTORY = "context.ProjectFileDirectory";

  @NonNls String MODALITY_STATE = "ModalityState";

  /**
   * returns Boolean
   */
  @NonNls String SOURCE_NAVIGATION_LOCKED = "sourceNavigationLocked";

  /**
   * returns array of ModuleGroups
   */
  @NonNls String MODULE_GROUP_ARRAY = "moduleGroup.array";

  /**
   * returns array of Forms
   */
  @NonNls String GUI_DESIGNER_FORM_ARRAY = "form.array";

  /**
   * returns array of LibraryGroups
   */
  @NonNls String LIBRARY_GROUP_ARRAY = "libraryGroup.array";

  /**
   * returns array of NamedLibraryElements
   */
  @NonNls String NAMED_LIBRARY_ARRAY = "namedLibrary.array";

  /**
   * returns com.intellij.openapi.fileEditor.impl.EditorWindow
   */
  @NonNls String EDITOR_WINDOW = "editorWindow";

  /**
   * returns array of com.intellij.lang.properties.ResourceBundle
   */
  @NonNls String RESOURCE_BUNDLE_ARRAY = "resource.bundle.array";

  /**
   * returns last focused JComponent
   */
  @NonNls String PREV_FOCUSED_COMPONENT = "";
}
