package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeComponent;
import com.intellij.openapi.util.IconLoader;

/**
 * @author nik
 */
public class SortElementsToggleAction extends ToggleAction implements DumbAware {
  private LayoutTreeComponent myLayoutTreeComponent;

  public SortElementsToggleAction(final LayoutTreeComponent layoutTreeComponent) {
    super("Sort", "Sort Elements by Names and Types", IconLoader.getIcon("/objectBrowser/sorted.png"));
    myLayoutTreeComponent = layoutTreeComponent;
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return myLayoutTreeComponent.isSortElements();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    myLayoutTreeComponent.setSortElements(state);
  }
}
