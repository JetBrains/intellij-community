package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.project.Project;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.speedSearch.SpeedSearch;

import javax.swing.*;
import java.awt.*;

public class OptionsEditor extends JPanel {

  Project myProject;
  private OptionsTree myTree;

  public OptionsEditor(Project project, ConfigurableGroup[] groups, Configurable preselectedConfigurable) {
    myProject = project;

    myTree = new OptionsTree(myProject, groups, new Filter());

    final JPanel left = new JPanel(new BorderLayout());
    left.add(myTree, BorderLayout.CENTER);

    setLayout(new BorderLayout());
    add(left, BorderLayout.CENTER);
  }


  private class Filter implements ElementFilter {
    public boolean shouldBeShowing(final Object value) {
      return true;
    }

    public SpeedSearch getSpeedSearch() {
      return new SpeedSearch() {
        protected void update() {
        }
      };
    }
  }
}