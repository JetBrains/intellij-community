package com.intellij.openapi.externalSystem.model;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.externalSystem.ui.ProjectStructureNode;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.openapi.externalSystem.ui.ExternalProjectStructureTreeModel;

import java.util.Collection;

/**
 * @author Denis Zhdanov
 * @since 2/7/12 11:19 AM
 */
public class ExternalSystemDataKeys {

  /** Key for obtaining 'external system project structure' tree. */
  public static final DataKey<Tree> PROJECT_TREE = DataKey.create("external.system.project.tree");

  /** Key for obtaining 'external system project structure' tree model. */
  public static final DataKey<ExternalProjectStructureTreeModel> PROJECT_TREE_MODEL = DataKey.create("external.system.project.tree.model");

  /** Key for obtaining currently selected nodes at the 'external system project structure' tree. */
  public static final DataKey<Collection<ProjectStructureNode<?>>> PROJECT_TREE_SELECTED_NODE
    = DataKey.create("gradle.sync.tree.node.selected");

  /** Key for obtaining node under mouse cursor at the 'external system project structure' tree. */
  public static final DataKey<ProjectStructureNode<?>> SYNC_TREE_NODE_UNDER_MOUSE
    = DataKey.create("gradle.sync.tree.node.under.mouse");
  
  // TODO den uncomment
  
//  public static final DataKey<GradleTasksList> RECENT_TASKS_LIST = DataKey.create("gradle.recent.tasks.list");

//  public static final DataKey<GradleTasksModel> ALL_TASKS_MODEL = DataKey.create("gradle.all.tasks.model");

  private ExternalSystemDataKeys() {
  }
}
