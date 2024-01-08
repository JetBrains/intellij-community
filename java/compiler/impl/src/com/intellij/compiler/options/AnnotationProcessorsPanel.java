// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.options;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.TreeUIHelper;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.SmartList;
import com.intellij.util.ui.EditableTreeModel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile;
import org.jetbrains.jps.model.java.impl.compiler.ProcessorConfigProfileImpl;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings({"unchecked", "UseOfObsoleteCollectionType"})
public class AnnotationProcessorsPanel extends JPanel {
  private final ProcessorConfigProfile myDefaultProfile = new ProcessorConfigProfileImpl("");
  private final List<ProcessorConfigProfile> myModuleProfiles = new ArrayList<>();
  private final Map<String, Module> myAllModulesMap = new HashMap<>();
  private final Project myProject;
  private final Tree myTree;
  private final ProcessorProfilePanel myProfilePanel;
  private ProcessorConfigProfile mySelectedProfile = null;

  public AnnotationProcessorsPanel(Project project) {
    super(new BorderLayout());
    Splitter splitter = new Splitter(false, 0.3f);
    add(splitter, BorderLayout.CENTER);
    myProject = project;
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      myAllModulesMap.put(module.getName(), module);
    }
    myTree = new Tree(new MyTreeModel());
    myTree.setRootVisible(false);
    JPanel treePanel = ToolbarDecorator.createDecorator(myTree)
      .addExtraAction(new MoveProfileAction())
      .createPanel();
    splitter.setFirstComponent(treePanel);
    myTree.setCellRenderer(new MyCellRenderer());
    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        final TreePath path = myTree.getSelectionPath();
        if (path != null) {
          Object node = path.getLastPathComponent();
          if (node instanceof MyModuleNode) {
            node = ((MyModuleNode)node).getParent();
          }
          if (node instanceof ProfileNode) {
            final ProcessorConfigProfile nodeProfile = ((ProfileNode)node).myProfile;
            final ProcessorConfigProfile selectedProfile = mySelectedProfile;
            if (nodeProfile != selectedProfile) {
              if (selectedProfile != null) {
                myProfilePanel.saveTo(selectedProfile);
              }
              mySelectedProfile = nodeProfile;
              myProfilePanel.setProfile(nodeProfile);
            }
          }
        }
      }
    });
    TreeUIHelper.getInstance().installTreeSpeedSearch(myTree, path -> {
      Object node = path.getLastPathComponent();
      if (node instanceof MyModuleNode moduleNode) {
        return moduleNode.getModule().getName();
      }
      else if (node instanceof ProfileNode profileNode) {
        return profileNode.myProfile.getName();
      }
      else {
        return "";
      }
    }, false);
    myProfilePanel = new ProcessorProfilePanel(project);
    myProfilePanel.setBorder(JBUI.Borders.emptyLeft(6));
    splitter.setSecondComponent(myProfilePanel);
  }

  public void initProfiles(ProcessorConfigProfile defaultProfile, Collection<? extends ProcessorConfigProfile> moduleProfiles) {
    myDefaultProfile.initFrom(defaultProfile);
    myModuleProfiles.clear();
    for (ProcessorConfigProfile profile : moduleProfiles) {
      ProcessorConfigProfile copy = new ProcessorConfigProfileImpl("");
      copy.initFrom(profile);
      myModuleProfiles.add(copy);
    }
    final RootNode root = (RootNode)myTree.getModel().getRoot();
    root.sync();
    final DefaultMutableTreeNode node = TreeUtil.findNodeWithObject(root, myDefaultProfile);
    if (node != null) {
      TreeUtil.selectNode(myTree, node);
    }
  }

  public ProcessorConfigProfile getDefaultProfile() {
    final ProcessorConfigProfile selectedProfile = mySelectedProfile;
    if (myDefaultProfile == selectedProfile) {
      myProfilePanel.saveTo(selectedProfile);
    }
    return myDefaultProfile;
  }

  public List<ProcessorConfigProfile> getModuleProfiles() {
    final ProcessorConfigProfile selectedProfile = mySelectedProfile;
    if (myDefaultProfile != selectedProfile) {
      myProfilePanel.saveTo(selectedProfile);
    }
    return Collections.unmodifiableList(myModuleProfiles);
  }

  private class MyTreeModel extends DefaultTreeModel implements EditableTreeModel{
    MyTreeModel() {
      super(new RootNode());
    }

    @Override
    public TreePath addNode(TreePath parentOrNeighbour) {
      final String newProfileName = Messages.showInputDialog(
        myProject, JavaCompilerBundle.message("dialog.message.profile.name"), JavaCompilerBundle.message("title.create.new.profile"), null, "",
        new InputValidatorEx() {
          @Override
          public boolean checkInput(String inputString) {
            if (StringUtil.isEmpty(inputString) ||
                Objects.equals(inputString, myDefaultProfile.getName())) {
              return false;
            }
            for (ProcessorConfigProfile profile : myModuleProfiles) {
              if (Objects.equals(inputString, profile.getName())) {
                return false;
              }
            }
            return true;
          }

          @Override
          public boolean canClose(String inputString) {
            return checkInput(inputString);
          }

          @Override
          public String getErrorText(String inputString) {
            if (checkInput(inputString)) {
              return null;
            }
            return StringUtil.isEmpty(inputString)
              ? JavaCompilerBundle.message("text.empty.profile")
              : JavaCompilerBundle.message("text.profile.exists", inputString);
          }
        });
      if (newProfileName != null) {
        final ProcessorConfigProfile profile = new ProcessorConfigProfileImpl(newProfileName);
        myModuleProfiles.add(profile);
        ((DataSynchronizable)getRoot()).sync();
        final DefaultMutableTreeNode object = TreeUtil.findNodeWithObject((DefaultMutableTreeNode)getRoot(), profile);
        if (object != null) {
          TreeUtil.selectNode(myTree, object);
        }
      }
      return null;
    }

    @Override
    public void removeNode(TreePath nodePath) {
      removeNodes(Collections.singleton(nodePath));
    }

    @Override
    public void removeNodes(Collection<? extends TreePath> paths) {
      final List<ProcessorConfigProfile> toRemove = new SmartList<>();
      for (TreePath path : paths) {
        Object node = path.getLastPathComponent();
        if (node instanceof ProfileNode) {
          final ProcessorConfigProfile nodeProfile = ((ProfileNode)node).myProfile;
          if (nodeProfile != myDefaultProfile) {
            toRemove.add(nodeProfile);
          }
        }
      }
      if (!toRemove.isEmpty()) {
        boolean changed = false;
        for (ProcessorConfigProfile nodeProfile : toRemove) {
          if (mySelectedProfile == nodeProfile) {
            mySelectedProfile = null;
          }
          changed |= myModuleProfiles.remove(nodeProfile);
        }
        if (changed) {
          ((DataSynchronizable)getRoot()).sync();
          final DefaultMutableTreeNode object = TreeUtil.findNodeWithObject((DefaultMutableTreeNode)getRoot(), myDefaultProfile);
          if (object != null) {
            TreeUtil.selectNode(myTree, object);
          }
        }
      }
    }

    @Override
    public void moveNodeTo(TreePath parentOrNeighbour) {
    }

  }


  private class RootNode extends DefaultMutableTreeNode implements DataSynchronizable {
    @Override
    public DataSynchronizable sync() {
      final Vector<TreeNode> newKids =  new Vector<>();
      newKids.add(new ProfileNode(myDefaultProfile, this, true).sync());
      for (ProcessorConfigProfile profile : myModuleProfiles) {
        newKids.add(new ProfileNode(profile, this, false).sync());
      }
      children = newKids;
      ((DefaultTreeModel)myTree.getModel()).reload();
      TreeUtil.expandAll(myTree);
      return this;
    }
  }

  private interface DataSynchronizable extends TreeNode {
    DataSynchronizable sync();
  }

  private class ProfileNode extends DefaultMutableTreeNode implements DataSynchronizable {
    private final ProcessorConfigProfile myProfile;
    private final boolean myIsDefault;

    ProfileNode(ProcessorConfigProfile profile, RootNode parent, boolean isDefault) {
      super(profile);
      setParent(parent);
      myIsDefault = isDefault;
      myProfile = profile;
    }

    @Override
    public DataSynchronizable sync() {
      final List<Module> nodeModules = new ArrayList<>();
      if (myIsDefault) {
        final Set<String> nonDefaultProfileModules = new HashSet<>();
        for (ProcessorConfigProfile profile : myModuleProfiles) {
          nonDefaultProfileModules.addAll(profile.getModuleNames());
        }
        for (Map.Entry<String, Module> entry : myAllModulesMap.entrySet()) {
          if (!nonDefaultProfileModules.contains(entry.getKey())) {
            nodeModules.add(entry.getValue());
          }
        }
      }
      else {
        for (String moduleName : myProfile.getModuleNames()) {
          final Module module = myAllModulesMap.get(moduleName);
          if (module != null) {
            nodeModules.add(module);
          }
        }
      }
      nodeModules.sort(ModuleComparator.INSTANCE);
      final Vector<TreeNode> vector = new Vector<>();
      for (Module module : nodeModules) {
        vector.add(new MyModuleNode(module, this));
      }
      children = vector;
      return this;
    }

  }

  private static class MyModuleNode extends DefaultMutableTreeNode {
    MyModuleNode(Module module, ProfileNode parent) {
      super(module);
      setParent(parent);
      setAllowsChildren(false);
    }

    Module getModule() {
      return (Module)getUserObject();
    }
  }

  private static class MyCellRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      if (value instanceof ProfileNode) {
        append(((ProfileNode)value).myProfile.getName());
      }
      else if (value instanceof MyModuleNode) {
        final Module module = ((MyModuleNode)value).getModule();
        setIcon(AllIcons.Nodes.Module);
        append(module.getName());
      }
    }
  }

  private static class ModuleComparator implements Comparator<Module> {
    static final ModuleComparator INSTANCE = new ModuleComparator();
    @Override
    public int compare(Module o1, Module o2) {
      return o1.getName().compareTo(o2.getName());
    }
  }

  private class MoveProfileAction extends DumbAwareAction {

    MoveProfileAction() {
      super(JavaCompilerBundle.messagePointer("action.text.move.to"), AllIcons.Actions.Forward);
      copyShortcutFrom(ActionManager.getInstance().getAction("Move"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final MyModuleNode node = (MyModuleNode)myTree.getSelectionPath().getLastPathComponent();
      final TreePath[] selectedNodes = myTree.getSelectionPaths();
      final ProcessorConfigProfile nodeProfile = ((ProfileNode)node.getParent()).myProfile;
      final List<ProcessorConfigProfile> profiles = new ArrayList<>();
      profiles.add(myDefaultProfile);
      profiles.addAll(myModuleProfiles);
      profiles.remove(nodeProfile);
      final JBPopup popup = JBPopupFactory.getInstance()
        .createPopupChooserBuilder(profiles)
        .setTitle(JavaCompilerBundle.message("action.text.move.to"))
        .setItemChosenCallback((chosenProfile) -> {
          final Module toSelect = node.getModule();
          if (selectedNodes != null) {
            for (TreePath selectedNode : selectedNodes) {
              final Object node1 = selectedNode.getLastPathComponent();
              if (node1 instanceof MyModuleNode) {
                final Module module = ((MyModuleNode)node1).getModule();
                if (nodeProfile != myDefaultProfile) {
                  nodeProfile.removeModuleName(module.getName());
                }
                if (chosenProfile != myDefaultProfile) {
                  chosenProfile.addModuleName(module.getName());
                }
              }
            }
          }

          final RootNode root = (RootNode)myTree.getModel().getRoot();
          root.sync();
          final DefaultMutableTreeNode node1 = TreeUtil.findNodeWithObject(root, toSelect);
          if (node1 != null) {
            TreeUtil.selectNode(myTree, node1);
          }
        }).createPopup();
      popup.show(JBPopupFactory.getInstance().guessBestPopupLocation(this, e));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      TreePath selectionPath = myTree.getSelectionPath();
      e.getPresentation().setEnabled(
        !myModuleProfiles.isEmpty() &&
        selectionPath != null && selectionPath.getLastPathComponent() instanceof MyModuleNode);
    }
  }
}
