package com.intellij.slicer;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.util.treeView.AbstractTreeStructureBase;
import com.intellij.openapi.project.Project;

import java.util.Collections;
import java.util.List;

/**
 * @author cdr
 */
public class SliceTreeStructure extends AbstractTreeStructureBase {
  private final SliceNode myRoot;

  public SliceTreeStructure(Project project, SliceNode rootNode) {
    super(project);
    myRoot = rootNode;
  }

  public List<TreeStructureProvider> getProviders() {
    return Collections.emptyList();
  }

  public Object getRootElement() {
    return myRoot;
  }

  public void commit() {

  }

  public boolean hasSomethingToCommit() {
    return false;
  }

  @Override
  public boolean isToBuildChildrenInBackground(final Object element) {
    return true;//!ApplicationManager.getApplication().isUnitTestMode();
  }
}
