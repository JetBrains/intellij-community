/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.internal;

import com.intellij.codeInsight.hint.ImplementationViewComponent;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.PropertyName;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Function;
import com.intellij.util.NotNullFunction;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("UseOfObsoleteCollectionType")
public class ImageDuplicateResultsDialog extends DialogWrapper {
  private final Project myProject;
  private final List<VirtualFile> myImages;
  private final Map<String, Set<VirtualFile>> myDuplicates;
  private final Tree myTree;
  private final ResourceModules myResourceModules = new ResourceModules();


  public ImageDuplicateResultsDialog(Project project, List<VirtualFile> images, Map<String, Set<VirtualFile>> duplicates) {
    super(project);
    myProject = project;
    myImages = images;
    PropertiesComponent.getInstance(myProject).loadFields(myResourceModules);
    myDuplicates = duplicates;
    setModal(false);
    myTree = new Tree(new MyRootNode());
    myTree.setRootVisible(true);
    myTree.setCellRenderer(new MyCellRenderer());
    init();
    TreeUtil.expandAll(myTree);
    setTitle("Image Duplicates");
    TreeUtil.selectFirstNode(myTree);
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    final Action[] actions = new Action[4];
    actions[0] = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
      }
    };
    actions[0].putValue(Action.NAME, "Fix all");
    actions[0].putValue(DEFAULT_ACTION, Boolean.TRUE);
    actions[0].putValue(FOCUSED_ACTION, Boolean.TRUE);
    actions[1] = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
      }
    };
    actions[1].putValue(Action.NAME, "Fix selected");
    actions[2] = getCancelAction();
    actions[3] = getHelpAction();
    //return actions;
    return new Action[0];
  }

  @Override
  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    DataManager.registerDataProvider(panel, new DataProvider() {
      @Override
      public Object getData(@NonNls String dataId) {
        final TreePath path = myTree.getSelectionPath();
        if (path != null) {
          Object component = path.getLastPathComponent();
          VirtualFile file = null;
          if (component instanceof MyFileNode) {
            component = ((MyFileNode)component).getParent();
          }
          if (component instanceof MyDuplicatesNode) {
            file = ((MyDuplicatesNode)component).getUserObject().iterator().next();
          }
          if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
            return file;
          }
          if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId) && file != null) {
            return new VirtualFile[]{file};
          }
        }
        return null;
      }
    });

    final JBList list = new JBList(new ResourceModules().getModuleNames());
    final NotNullFunction<Object, JComponent> modulesRenderer =
      dom -> new JLabel(dom instanceof Module ? ((Module)dom).getName() : dom.toString(), PlatformIcons.SOURCE_FOLDERS_ICON, SwingConstants.LEFT);
    list.installCellRenderer(modulesRenderer);
    final JPanel modulesPanel = ToolbarDecorator.createDecorator(list)
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          final Module[] all = ModuleManager.getInstance(myProject).getModules();
          Arrays.sort(all, (o1, o2) -> o1.getName().compareTo(o2.getName()));
          final JBList modules = new JBList(all);
          modules.installCellRenderer(modulesRenderer);
          JBPopupFactory.getInstance().createListPopupBuilder(modules)
            .setTitle("Add Resource Module")
            .setFilteringEnabled(o -> ((Module)o).getName())
            .setItemChoosenCallback(() -> {
              final Object value = modules.getSelectedValue();
              if (value instanceof Module && !myResourceModules.contains((Module)value)) {
                myResourceModules.add((Module)value);
                ((DefaultListModel)list.getModel()).addElement(((Module)value).getName());
              }
              ((DefaultTreeModel)myTree.getModel()).reload();
              TreeUtil.expandAll(myTree);
            }).createPopup().show(button.getPreferredPopupPoint());
        }
      })
      .setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          final Object[] values = list.getSelectedValues();
          for (Object value : values) {
            myResourceModules.remove((String)value);
            ((DefaultListModel)list.getModel()).removeElement(value);
          }
          ((DefaultTreeModel)myTree.getModel()).reload();
          TreeUtil.expandAll(myTree);
        }
      })
      .disableDownAction()
      .disableUpAction()
      .createPanel();
    modulesPanel.setPreferredSize(new Dimension(-1, 60));
    final JPanel top = new JPanel(new BorderLayout());
    top.add(new JLabel("Image modules:"), BorderLayout.NORTH);
    top.add(modulesPanel, BorderLayout.CENTER);

    panel.add(top, BorderLayout.NORTH);
    panel.add(new JBScrollPane(myTree), BorderLayout.CENTER);
    new AnAction() {

      @Override
      public void actionPerformed(AnActionEvent e) {
        VirtualFile file = getFileFromSelection();
        if (file != null) {
          final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
          if (psiFile != null) {
            final ImplementationViewComponent viewComponent = new ImplementationViewComponent(new PsiElement[]{psiFile}, 0);
            final TreeSelectionListener listener = new TreeSelectionListener() {
              @Override
              public void valueChanged(TreeSelectionEvent e) {
                final VirtualFile selection = getFileFromSelection();
                if (selection != null) {
                  final PsiFile newElement = PsiManager.getInstance(myProject).findFile(selection);
                  if (newElement != null) {
                    viewComponent.update(new PsiElement[]{newElement}, 0);
                  }
                }
              }
            };
            myTree.addTreeSelectionListener(listener);

            final JBPopup popup =
              JBPopupFactory.getInstance().createComponentPopupBuilder(viewComponent, viewComponent.getPreferredFocusableComponent())
                .setProject(myProject)
                .setDimensionServiceKey(myProject, ImageDuplicateResultsDialog.class.getName(), false)
                .setResizable(true)
                .setMovable(true)
                .setRequestFocus(false)
                .setCancelCallback(() -> {
                  myTree.removeTreeSelectionListener(listener);
                  return true;
                })
                .setTitle("Image Preview")
                .createPopup();


            final Window window = ImageDuplicateResultsDialog.this.getWindow();
            popup.show(new RelativePoint(window, new Point(window.getWidth(), 0)));
            viewComponent.setHint(popup, "Image Preview");
          }
        }
      }
    }.registerCustomShortcutSet(CustomShortcutSet.fromString("ENTER"), panel);

    int total = 0;
    for (Set set : myDuplicates.values()) total+=set.size();
    total-=myDuplicates.size();
    final JLabel label = new JLabel(
      "<html>Press <b>Enter</b> to preview image<br>Total images found: " + myImages.size() + ". Total duplicates found: " + total+"</html>");
    panel.add(label, BorderLayout.SOUTH);
    return panel;
  }

  @Override
  protected String getDimensionServiceKey() {
    return "image.duplicates.dialog";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTree;
  }

  @Nullable
  private VirtualFile getFileFromSelection() {
    final TreePath path = myTree.getSelectionPath();
    if (path != null) {
      Object component = path.getLastPathComponent();
      VirtualFile file = null;
      if (component instanceof MyFileNode) {
        component = ((MyFileNode)component).getParent();
      }
      if (component instanceof MyDuplicatesNode) {
        file = ((MyDuplicatesNode)component).getUserObject().iterator().next();
      }
      return file;
    }
    return null;
  }


  private class MyRootNode extends DefaultMutableTreeNode {
    private MyRootNode() {
      final Vector vector = new Vector();
      for (Set<VirtualFile> files : myDuplicates.values()) {
        vector.add(new MyDuplicatesNode(this, files));
      }
      children = vector;
    }
  }


  private class MyDuplicatesNode extends DefaultMutableTreeNode {
    private final Set<VirtualFile> myFiles;

    public MyDuplicatesNode(DefaultMutableTreeNode node, Set<VirtualFile> files) {
      super(files);
      myFiles = files;
      setParent(node);
      final Vector vector = new Vector();
      for (VirtualFile file : files) {
        vector.add(new MyFileNode(this, file));
      }
      children = vector;
    }

    @Override
    public Set<VirtualFile> getUserObject() {
      return (Set<VirtualFile>)super.getUserObject();
    }
  }

  private static class MyFileNode extends DefaultMutableTreeNode {
    public MyFileNode(DefaultMutableTreeNode node, VirtualFile file) {
      super(file);
      setParent(node);
    }

    @Override
    public VirtualFile getUserObject() {
      return (VirtualFile)super.getUserObject();
    }
  }

  private class MyCellRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      if (value instanceof MyFileNode) {
        final VirtualFile file = ((MyFileNode)value).getUserObject();
        final Module module = ModuleUtil.findModuleForFile(file, myProject);
        if (module != null) {
          setIcon(PlatformIcons.CONTENT_ROOT_ICON_CLOSED);
          append("[" + module.getName() + "] ", new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, UIUtil.getTreeForeground()));
          append(getRelativePathToProject(myProject, file));
        }
        else {
          append(getRelativePathToProject(myProject, file));
        }
      }
      else if (value instanceof MyDuplicatesNode) {
        final Set<VirtualFile> files = ((MyDuplicatesNode)value).getUserObject();
        for (VirtualFile file : files) {
          final Module module = ModuleUtil.findModuleForFile(file, myProject);

          if (module != null && myResourceModules.contains(module)) {
            append("Icons can be replaced to ");
            append(getRelativePathToProject(myProject, file),
                   new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, ColorUtil.fromHex("008000")));
            return;
          }
        }
        append("Icon conflict");
      } else if (value instanceof MyRootNode) {
        append("All conflicts");
      }
    }
  }

  private static String getRelativePathToProject(Project project, VirtualFile file) {
    final String path = project.getBasePath();
    assert path != null;
    final String result = FileUtil.getRelativePath(path, file.getPath().replace('/', File.separatorChar), File.separatorChar);
    assert result != null;
    return result;
  }



  static class ResourceModules {
    @PropertyName(value = "resource.modules", defaultValue = "icons")
    public String modules;

    public List<String> getModuleNames() {
      return Arrays.asList(StringUtil.splitByLines(modules == null ? "icons" : modules));
    }

    public boolean contains(Module module) {
      return getModuleNames().contains(module.getName());
    }

    public void add(Module module) {
      if (StringUtil.isEmpty(modules)) {
        modules = module.getName();
      } else {
        modules += "\n" + module.getName();
      }
    }

    public void remove(String value) {
      final List<String> names = new ArrayList<>(getModuleNames());
      names.remove(value);
      modules = StringUtil.join(names, "\n");
    }
  }
}
