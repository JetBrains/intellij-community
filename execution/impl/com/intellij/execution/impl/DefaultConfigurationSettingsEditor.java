package com.intellij.execution.impl;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TreeToolTipHandler;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * User: anna
 * Date: 29-Mar-2006
 */
public class DefaultConfigurationSettingsEditor implements Configurable {

  @NonNls private DefaultMutableTreeNode myRoot = new DefaultMutableTreeNode("Root");
  private JTree myTree = new JTree(myRoot);
  private Project myProject;
  private Map<ConfigurationType, Configurable> myStoredComponents = new HashMap<ConfigurationType, Configurable>();
  private ConfigurationType mySelection;

  public DefaultConfigurationSettingsEditor(Project project, ConfigurationType selection) {
    myProject = project;
    mySelection = selection;
  }

  public JComponent createComponent() {
    final JPanel wholePanel = new JPanel(new BorderLayout());
    final JScrollPane pane = ScrollPaneFactory.createScrollPane(myTree);
    pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    pane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
    wholePanel.add(pane, BorderLayout.WEST);
    final JPanel rightPanel = new JPanel(new BorderLayout());
    wholePanel.add(rightPanel, BorderLayout.CENTER);
    final ConfigurationType[] configurationTypes = RunManagerImpl.getInstanceImpl(myProject).getConfigurationFactories();
    for (ConfigurationType type : configurationTypes) {
      myRoot.add(new DefaultMutableTreeNode(type));
    }
    myTree.setRootVisible(false);
    TreeToolTipHandler.install(myTree);
    TreeUtil.installActions(myTree);
    myTree.setCellRenderer(new DefaultTreeCellRenderer() {
      public Component getTreeCellRendererComponent(JTree tree,
                                                    Object value,
                                                    boolean sel,
                                                    boolean expanded,
                                                    boolean leaf,
                                                    int row,
                                                    boolean hasFocus) {
        final Component rendererComponent = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        if (value instanceof DefaultMutableTreeNode) {
          final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
          if (userObject instanceof ConfigurationType) {
            final ConfigurationType type = (ConfigurationType)userObject;
            setText(type.getDisplayName());
            setIcon(type.getIcon());
          }
        }
        return rendererComponent;
      }
    });
    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        final TreePath selectionPath = myTree.getSelectionPath();
        if (selectionPath != null) {
          final DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
          final ConfigurationType type = (ConfigurationType)node.getUserObject();
          rightPanel.removeAll();
          Configurable configurable = myStoredComponents.get(type);
          if (configurable == null){
            configurable = TypeTemplatesConfigurable.createConfigurable(type, myProject);
            myStoredComponents.put(type, configurable);
            rightPanel.add(configurable.createComponent());
            configurable.reset();
          } else {
            rightPanel.add(configurable.createComponent());
          }
          rightPanel.revalidate();
          rightPanel.repaint();
          final Window window = SwingUtilities.windowForComponent(wholePanel);
          if (window != null &&
              (window.getSize().height < window.getMinimumSize().height ||
               window.getSize().width < window.getMinimumSize().width)) {
            window.pack();
          }
        }
      }
    });
    RunConfigurable.sortTree(myRoot);
    ((DefaultTreeModel)myTree.getModel()).reload();
    TreeUtil.selectFirstNode(myTree);
    TreeUtil.traverse(myRoot, new TreeUtil.Traverse() {
      public boolean accept(Object node) {
        if (node instanceof DefaultMutableTreeNode){
          final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)node;
          final Object o = treeNode.getUserObject();
          if (Comparing.equal(o, mySelection)){
            TreeUtil.selectInTree(treeNode, true, myTree);
            return false;
          }
        }
        return true;
      }
    });
    return wholePanel;
  }

  public boolean isModified() {
    for (Configurable configurable : myStoredComponents.values()) {
      if (configurable.isModified()) return true;
    }
    return false;
  }

  public void apply() throws ConfigurationException {
    for (Configurable configurable : myStoredComponents.values()) {
      if (configurable.isModified()){
        configurable.apply();
      }
    }
  }

  public void reset() {
    //do nothing
  }

  public void disposeUIResources() {
    for (Configurable configurable : myStoredComponents.values()) {
      configurable.disposeUIResources();
    }
  }

  public String getDisplayName() {
    return ExecutionBundle.message("default.settings.editor.dialog.title");
  }

  public Icon getIcon() {
    return null;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return null;
  }
}
