/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.util;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.ide.structureView.newStructureView.TreeActionsOwner;
import com.intellij.ide.structureView.newStructureView.TreeModelWrapper;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.smartTree.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SpeedSearchBase;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.filtered.FilteringTreeBuilder;
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Konstantin Bulenkov
 */
public class FileStructurePopup implements Disposable {
  private final Editor myEditor;
  private final Project myProject;
  private final StructureViewModel myTreeModel;
  private final StructureViewModel myBaseTreeModel;
  @NotNull private final Disposable myDisposable;
  private final MyTreeActionsOwner myTreeActionsOwner;
  private JBPopup myPopup;

  @NonNls private static final String ourPropertyKey = "FileStructure.narrowDown";
  private boolean myShouldNarrowDown = false;
  private Tree myTree;
  private FilteringTreeBuilder myAbstractTreeBuilder;
  private String myTitle;
  private TreeSpeedSearch mySpeedSearch;
  private SmartTreeStructure myTreeStructure;
  private int myPrefferedWidth;

  public FileStructurePopup(StructureViewModel structureViewModel,
                            @Nullable Editor editor,
                            Project project,
                            @NotNull final Disposable auxDisposable,
                            final boolean applySortAndFilter) {
    myProject = project;
    myEditor = editor;
    myBaseTreeModel = structureViewModel;
    myDisposable = auxDisposable;
    if (applySortAndFilter) {
      myTreeActionsOwner = new MyTreeActionsOwner();
      myTreeModel = new TreeModelWrapper(structureViewModel, myTreeActionsOwner);
    }
    else {
      myTreeActionsOwner = null;
      myTreeModel = structureViewModel;
    }    

    myTreeStructure = new SmartTreeStructure(project, myTreeModel){
      public void rebuildTree() {
        if (!myPopup.isDisposed()) {
          super.rebuildTree();
        }
      }

      public boolean isToBuildChildrenInBackground(final Object element) {
        return getRootElement() == element;
      }

      protected TreeElementWrapper createTree() {
        return new StructureViewComponent.StructureViewTreeElementWrapper(myProject, myModel.getRoot(), myModel);
      }

      @Override
      public String toString() {
        return "structure view tree structure(model=" + myTreeModel + ")";
      }
    };
    myTree = new Tree(new DefaultMutableTreeNode(myTreeStructure.getRootElement()));
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);

    mySpeedSearch = new TreeSpeedSearch(myTree, TreeSpeedSearch.NODE_DESCRIPTOR_TOSTRING, true) {
      @Override
      protected Point getComponentLocationOnScreen() {
        return myPopup.getContent().getLocationOnScreen();
      }

      @Override
      protected Rectangle getComponentVisibleRect() {
        return myPopup.getContent().getVisibleRect();
      }
    };
    myAbstractTreeBuilder = new FilteringTreeBuilder(project, myTree, new FileStructurePopupFilter(), myTreeStructure, null) {
      @Override
      protected boolean validateNode(Object child) {
        return StructureViewComponent.isValid(child);
      }

      @Override
      public void revalidateTree() {
        //myTree.revalidate();
        //myTree.repaint();
      }
    };
    myAbstractTreeBuilder.setCanYieldUpdate(true);
  }

  public void show() {
    JComponent panel = createCenterPanel();
    new MnemonicHelper().register(panel);
    boolean shouldSetWidth = DimensionService.getInstance().getSize(getDimensionServiceKey(), myProject) == null;
    myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, null)
      .setTitle(myTitle)
      .setResizable(true)
      .setFocusable(true)
      .setMovable(true)
      .setCancelKeyEnabled(false)
      .addUserData("ShowHints")
      .setDimensionServiceKey(null, getDimensionServiceKey(), false)
      .createPopup();
    Disposer.register(myPopup, myDisposable);
    Disposer.register(myPopup, this);
    Disposer.register(myPopup, myAbstractTreeBuilder);
    myPopup.showInBestPositionFor(myEditor);

    ((AbstractPopup)myPopup).setShowHints(true);
    if (shouldSetWidth) {
      myPopup.setSize(new Dimension(myPrefferedWidth + 10, myPopup.getSize().height));
    }
    new Alarm().addRequest(new Runnable() {
      @Override
      public void run() {
        myAbstractTreeBuilder.expandAll(new Runnable() {
          @Override
          public void run() {
            IdeFocusManager.getInstance(myProject).requestFocus(myTree, true);
            myAbstractTreeBuilder.queueUpdate().doWhenDone(new Runnable() {
              @Override
              public void run() {
                myAbstractTreeBuilder.expandAll(null);
                selectPsiElement(getCurrentElement(getPsiFile(myProject)));
              }
            });
          }
        });
      }
    }, 100);
    final Alarm alarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD, myPopup);
    alarm.addRequest(new Runnable() {
      String filter = "";
      @Override
      public void run() {
        alarm.cancelAllRequests();
        String prefix = mySpeedSearch.getEnteredPrefix();
        myTree.getEmptyText().setText(StringUtil.isEmpty(prefix) ?  "Nothing to show" : "Can't find '" + prefix + "'");
        if (prefix == null) prefix = "";
        
        if (!filter.equals(prefix)) {
          filter = prefix;
          myAbstractTreeBuilder.refilter(null, false, false);
        }
        alarm.addRequest(this, 500);
      }
    }, 500);
  }

  private void selectPsiElement(PsiElement element) {
    Set<PsiElement> parents = new java.util.HashSet<PsiElement>();

    while (element != null) {
      parents.add(element);
      if (element instanceof PsiFile) break;
      element = element.getParent();
    }

    FilteringTreeStructure.Node node = (FilteringTreeStructure.Node)myAbstractTreeBuilder.getRootElement();
    while (node != null) {
      boolean changed = false;
      for (FilteringTreeStructure.Node n : node.children()) {
        final PsiElement psiElement = getPsi(n);
        if (psiElement != null && parents.contains(psiElement)) {
          node = n;
          changed = true;
          break;
        }
      }
      if (!changed) {
        myAbstractTreeBuilder.getUi().select(node, null);
        return;
      }
    }
  }

  @Nullable
  private PsiElement getPsi(FilteringTreeStructure.Node n) {
    final Object delegate = n.getDelegate();
    if (delegate instanceof StructureViewComponent.StructureViewTreeElementWrapper) {
      final TreeElement value = ((StructureViewComponent.StructureViewTreeElementWrapper)delegate).getValue();
      if (value instanceof StructureViewTreeElement) {
        final Object element = ((StructureViewTreeElement)value).getValue();
        if (element instanceof PsiElement) {
          return (PsiElement)element;
        }
      }
    }
    return null;
  }

  @Nullable
  protected PsiFile getPsiFile(final Project project) {
    return PsiDocumentManager.getInstance(project).getPsiFile(myEditor.getDocument());
  }

  public void dispose() {
  }

  protected static String getDimensionServiceKey() {
    return "StructurePopup";
  }

  @Nullable
  protected PsiElement getCurrentElement(@Nullable final PsiFile psiFile) {
    if (psiFile == null) return null;

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    Object elementAtCursor = myTreeModel.getCurrentEditorElement();
    if (elementAtCursor instanceof PsiElement) {
      return (PsiElement)elementAtCursor;
    }

    return null;
  }

  protected JComponent createCenterPanel() {
    List<FileStructureFilter> fileStructureFilters = new ArrayList<FileStructureFilter>();
    List<FileStructureNodeProvider> fileStructureNodeProviders = new ArrayList<FileStructureNodeProvider>();
    if (myTreeActionsOwner != null) {
      for(Filter filter: myBaseTreeModel.getFilters()) {
        if (filter instanceof FileStructureFilter) {
          final FileStructureFilter fsFilter = (FileStructureFilter)filter;
          myTreeActionsOwner.setActionIncluded(fsFilter, true);
          fileStructureFilters.add(fsFilter);
        }
      }

      if (myBaseTreeModel instanceof ProvidingTreeModel) {
        for (NodeProvider provider : ((ProvidingTreeModel)myBaseTreeModel).getNodeProviders()) {
          if (provider instanceof FileStructureNodeProvider) {
            fileStructureNodeProviders.add((FileStructureNodeProvider)provider);
          }
        }
      }
    }
    final JPanel panel = new JPanel(new BorderLayout());
    JPanel comboPanel = new JPanel(new GridLayout(0, 2, 0, 0));

    final Shortcut[] F4 = ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE).getShortcutSet().getShortcuts();
    final Shortcut[] ENTER = CustomShortcutSet.fromString("ENTER").getShortcuts();
    final CustomShortcutSet shortcutSet = new CustomShortcutSet(ArrayUtil.mergeArrays(F4, ENTER));
    new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        final boolean succeeded = navigateSelectedElement();
        if (succeeded) {
          unregisterCustomShortcutSet(panel);
        }
      }
    }.registerCustomShortcutSet(shortcutSet, panel);

    new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        if (mySpeedSearch != null && mySpeedSearch.isPopupActive()) {
          mySpeedSearch.hidePopup();
        } else {
          myPopup.cancel();
        }
      }
    }.registerCustomShortcutSet(CustomShortcutSet.fromString("ESCAPE"), myTree);

    for(FileStructureFilter filter: fileStructureFilters) {
      addCheckbox(comboPanel, filter);
    }

    for (FileStructureNodeProvider provider : fileStructureNodeProviders) {
      addCheckbox(comboPanel, provider);
    }
    myPrefferedWidth = Math.max(comboPanel.getPreferredSize().width, 350);
    panel.add(comboPanel, BorderLayout.NORTH);
    panel.add(ScrollPaneFactory.createScrollPane(myAbstractTreeBuilder.getTree()), BorderLayout.CENTER);
    panel.add(createSouthPanel(), BorderLayout.SOUTH);

    return panel;
  }

  @Nullable
  private AbstractTreeNode getSelectedNode() {
    Object component = myTree.getSelectionPath().getLastPathComponent();
    if (component instanceof DefaultMutableTreeNode) {
      component = ((DefaultMutableTreeNode)component).getUserObject();
      if (component instanceof FilteringTreeStructure.Node) {
        component = ((FilteringTreeStructure.Node)component).getDelegate();
        if (component instanceof AbstractTreeNode) {
          return (AbstractTreeNode)component;
        }
      }
    }
    return null;
  }

  public boolean navigateSelectedElement() {
    final Ref<Boolean> succeeded = new Ref<Boolean>();
    final CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(myProject, new Runnable() {
      public void run() {
        final AbstractTreeNode selectedNode = getSelectedNode();
        if (selectedNode != null) {
          if (selectedNode.canNavigateToSource()) {
            selectedNode.navigate(true);
            succeeded.set(true);
          } else {
            succeeded.set(false);
          }
        } else {
          succeeded.set(false);
        }


        IdeDocumentHistory.getInstance(myProject).includeCurrentCommandAsNavigation();
      }
    }, "Navigate", null);
    if (succeeded.get()) {
      myPopup.cancel();
    }
    return succeeded.get();
  }

  private JComponent createSouthPanel() {
    final JCheckBox checkBox = new JCheckBox(IdeBundle.message("checkbox.narrow.down.the.list.on.typing"));
    checkBox.setSelected(PropertiesComponent.getInstance().isTrueValue(ourPropertyKey));
    checkBox.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        myShouldNarrowDown = checkBox.isSelected();
        PropertiesComponent.getInstance().setValue(ourPropertyKey, Boolean.toString(myShouldNarrowDown));

        myAbstractTreeBuilder.queueUpdate();
      }
    });

    checkBox.setFocusable(false);
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(checkBox, BorderLayout.WEST);
    return panel;
  }

  private void addCheckbox(final JPanel panel, final TreeAction action) {
    String text = action instanceof FileStructureFilter ? ((FileStructureFilter)action).getCheckBoxText() :
                  action instanceof FileStructureNodeProvider ? ((FileStructureNodeProvider)action).getCheckBoxText() : null;

    if (text == null) return;

    Shortcut[] shortcuts = action instanceof FileStructureFilter ?
                          ((FileStructureFilter)action).getShortcut() : ((FileStructureNodeProvider)action).getShortcut();


    final JCheckBox chkFilter = new JCheckBox();
    chkFilter.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final boolean state = chkFilter.isSelected();
        myTreeActionsOwner.setActionIncluded(action, action instanceof FileStructureFilter ? !state : state);
        myTreeStructure.rebuildTree();
        myAbstractTreeBuilder.refilter();
        myAbstractTreeBuilder.queueUpdate();

        if (SpeedSearchBase.hasActiveSpeedSearch(myTree)) {
          final SpeedSearchSupply supply = SpeedSearchSupply.getSupply(myTree);
          if (supply != null && supply.isPopupActive()) supply.refreshSelection();
        }
      }
    });
    chkFilter.setFocusable(false);

    if (shortcuts.length > 0) {
      text += " (" + KeymapUtil.getShortcutText(shortcuts[0]) + ")";
      new AnAction() {
        public void actionPerformed(final AnActionEvent e) {
          chkFilter.doClick();
        }
      }.registerCustomShortcutSet(new CustomShortcutSet(shortcuts), myTree);
    }
    chkFilter.setText(text);
    panel.add(chkFilter);
  }

  public void setTitle(String title) {
    myTitle = title;
  }

  private class MyTreeActionsOwner implements TreeActionsOwner {
    private final Set<TreeAction> myActions = new HashSet<TreeAction>();

    public void setActionActive(String name, boolean state) {
    }

    public boolean isActionActive(String name) {
      for (final Sorter sorter : myBaseTreeModel.getSorters()) {
        if (sorter.getName().equals(name)) {
          if (!sorter.isVisible()) return true;
        }
      }
      for(TreeAction action: myActions) {
        if (action.getName().equals(name)) return true;
      }
      return Sorter.ALPHA_SORTER_ID.equals(name);
    }

    public void setActionIncluded(final TreeAction filter, final boolean selected) {
      if (selected) {
        myActions.add(filter);
      }
      else {
        myActions.remove(filter);
      }
    }
  }

  //private class MyFilter extends ElementFilter.Active.Impl<StructureViewComponent.StructureViewTreeElementWrapper> {
  //
  //  @Override
  //  public boolean shouldBeShowing(StructureViewComponent.StructureViewTreeElementWrapper value) {
  //    return true;
  //  }
  //}


  private class FileStructurePopupFilter implements ElementFilter {
    private String myLastFilter = null;
    private HashSet<Object> myVisibleParents = new HashSet<Object>();
    @Override
    public boolean shouldBeShowing(Object value) {
      if (!myShouldNarrowDown) return true;

      String filter = mySpeedSearch != null && !StringUtil.isEmpty(mySpeedSearch.getEnteredPrefix()) 
                      ? mySpeedSearch.getEnteredPrefix() : null;
      if (!StringUtil.equals(myLastFilter, filter)) {
        myVisibleParents.clear();
        myLastFilter = filter;
      }
      if (filter != null) {
        if (myVisibleParents.contains(value)) {
          return true;
        }

        final String text = value.toString();
        if (text == null) return false;
        final Iterable<TextRange> ranges = mySpeedSearch.matchingFragments(text);
        boolean matches = ranges != null && ranges.iterator().hasNext();
        
        if (matches) {
          Object o = value;
          while (o instanceof FilteringTreeStructure.Node && (o = ((FilteringTreeStructure.Node)o).getParent()) != null) {
            myVisibleParents.add(o);
          }
          return true;
        } else {
          return false;
        }
        
      } 
      return true;
    }
  }
}
