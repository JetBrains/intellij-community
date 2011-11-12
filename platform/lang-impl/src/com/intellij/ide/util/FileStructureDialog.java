/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.ide.commander.CommanderPanel;
import com.intellij.ide.commander.ProjectListBuilder;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.structureView.newStructureView.TreeActionsOwner;
import com.intellij.ide.structureView.newStructureView.TreeModelWrapper;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.smartTree.*;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ListScrollingUtil;
import com.intellij.ui.SideBorder;
import com.intellij.ui.SpeedSearchBase;
import com.intellij.ui.docking.DockManager;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class FileStructureDialog extends DialogWrapper {
  private final Editor myEditor;
  private final Navigatable myNavigatable;
  private final Project myProject;
  private MyCommanderPanel myCommanderPanel;
  private final StructureViewModel myTreeModel;
  private final StructureViewModel myBaseTreeModel;
  private SmartTreeStructure myTreeStructure;
  private final MyTreeActionsOwner myTreeActionsOwner;

  @NonNls private static final String ourPropertyKey = "FileStructure.narrowDown";
  private boolean myShouldNarrowDown = false;

  public FileStructureDialog(StructureViewModel structureViewModel,
                             @Nullable Editor editor,
                             Project project,
                             Navigatable navigatable,
                             @NotNull final Disposable auxDisposable,
                             final boolean applySortAndFilter) {
    super(project, true);
    myProject = project;
    myEditor = editor;
    myNavigatable = navigatable;
    myBaseTreeModel = structureViewModel;
    if (applySortAndFilter) {
      myTreeActionsOwner = new MyTreeActionsOwner();
      myTreeModel = new TreeModelWrapper(structureViewModel, myTreeActionsOwner);
    }
    else {
      myTreeActionsOwner = null;
      myTreeModel = structureViewModel;
    }

    PsiFile psiFile = getPsiFile(project);

    final PsiElement psiElement = getCurrentElement(psiFile);

    //myDialog.setUndecorated(true);
    init();

    if (psiElement != null) {
      if (structureViewModel.shouldEnterElement(psiElement)) {
        myCommanderPanel.getBuilder().enterElement(psiElement, PsiUtilBase.getVirtualFile(psiElement));
      }
      else {
        myCommanderPanel.getBuilder().selectElement(psiElement, PsiUtilBase.getVirtualFile(psiElement));
      }
    }

    Disposer.register(myDisposable, auxDisposable);
  }

  protected PsiFile getPsiFile(final Project project) {
    return PsiDocumentManager.getInstance(project).getPsiFile(myEditor.getDocument());
  }

  @Nullable
  protected Border createContentPaneBorder() {
    return null;
  }

  public void dispose() {
    myCommanderPanel.dispose();
    super.dispose();
  }

  protected String getDimensionServiceKey() {
    return DockManager.getInstance(myProject).getDimensionKeyForFocus("#com.intellij.ide.util.FileStructureDialog");
  }

  public JComponent getPreferredFocusedComponent() {
    return IdeFocusTraversalPolicy.getPreferredFocusedComponent(myCommanderPanel);
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
    myCommanderPanel = new MyCommanderPanel(myProject);
    myTreeStructure = new MyStructureTreeStructure();

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

    PsiFile psiFile = getPsiFile(myProject);
    boolean showRoot = isShowRoot(psiFile);
    ProjectListBuilder projectListBuilder = new ProjectListBuilder(myProject, myCommanderPanel, myTreeStructure, null, showRoot) {
      @Override
      protected boolean shouldEnterSingleTopLevelElement(Object rootChild) {
        return myBaseTreeModel.shouldEnterElement(((StructureViewTreeElement)((AbstractTreeNode)rootChild).getValue()).getValue());
      }

      protected boolean nodeIsAcceptableForElement(AbstractTreeNode node, Object element) {
        return Comparing.equal(((StructureViewTreeElement)node.getValue()).getValue(), element);
      }

      protected void refreshSelection() {
        myCommanderPanel.scrollSelectionInView();
        if (myShouldNarrowDown) {
          myCommanderPanel.updateSpeedSearch();
        }
      }

      protected List<AbstractTreeNode> getAllAcceptableNodes(final Object[] childElements, VirtualFile file) {
        ArrayList<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
        for (Object childElement : childElements) {
          result.add((AbstractTreeNode)childElement);
        }
        return result;
      }
    };
    myCommanderPanel.setBuilder(projectListBuilder);
    myCommanderPanel.setTitlePanelVisible(false);

    new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        final boolean succeeded = myCommanderPanel.navigateSelectedElement();
        if (succeeded) {
          unregisterCustomShortcutSet(myCommanderPanel);
        }
      }
    }.registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE).getShortcutSet(), myCommanderPanel);

    myCommanderPanel.setPreferredSize(new Dimension(400, 500));

    JPanel panel = new JPanel(new BorderLayout());
    JPanel comboPanel = new JPanel(new GridLayout(0, 2, 0, 0));

    addNarrowDownCheckbox(comboPanel);

    for(FileStructureFilter filter: fileStructureFilters) {
      addCheckbox(comboPanel, filter);
    }

    for (FileStructureNodeProvider provider : fileStructureNodeProviders) {
      addCheckbox(comboPanel, provider);
    }

    myCommanderPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP));
    panel.add(comboPanel, BorderLayout.NORTH);
    panel.add(myCommanderPanel, BorderLayout.CENTER);
              //new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));

    return panel;
  }

  protected boolean isShowRoot(final PsiFile psiFile) {
    StructureViewBuilder viewBuilder = LanguageStructureViewBuilder.INSTANCE.getStructureViewBuilder(psiFile);
    return viewBuilder instanceof TreeBasedStructureViewBuilder && ((TreeBasedStructureViewBuilder)viewBuilder).isRootNodeShown();
  }

  private void addNarrowDownCheckbox(final JPanel panel) {
    final JCheckBox checkBox = new JCheckBox(IdeBundle.message("checkbox.narrow.down.the.list.on.typing"));
    checkBox.setSelected(PropertiesComponent.getInstance().isTrueValue(ourPropertyKey));
    checkBox.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        myShouldNarrowDown = checkBox.isSelected();
        PropertiesComponent.getInstance().setValue(ourPropertyKey, Boolean.toString(myShouldNarrowDown));

        ProjectListBuilder builder = (ProjectListBuilder)myCommanderPanel.getBuilder();
        if (builder == null) {
          return;
        }
        builder.addUpdateRequest();
      }
    });

    checkBox.setFocusable(false);
    panel.add(checkBox);
    //,new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 5, 0, 5), 0, 0));
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
        ProjectListBuilder builder = (ProjectListBuilder)myCommanderPanel.getBuilder();
        PsiElement currentParent = null;
        if (builder != null) {
          final AbstractTreeNode parentNode = builder.getParentNode();
          final Object value = parentNode.getValue();
          if (value instanceof StructureViewTreeElement) {
            final Object elementValue = ((StructureViewTreeElement)value).getValue();
            if (elementValue instanceof PsiElement) {
              currentParent = (PsiElement) elementValue;
            }
          }
        }
        final boolean state = chkFilter.isSelected();
        myTreeActionsOwner.setActionIncluded(action, action instanceof FileStructureFilter ? !state : state);
        myTreeStructure.rebuildTree();
        if (builder != null) {
          if (currentParent != null) {
            boolean oldNarrowDown = myShouldNarrowDown;
            myShouldNarrowDown = false;
            try {
              builder.enterElement(currentParent, PsiUtilBase.getVirtualFile(currentParent));
            }
            finally {
              myShouldNarrowDown = oldNarrowDown;
            }
          }
          builder.updateList(true);
        }

        if (SpeedSearchBase.hasActiveSpeedSearch(myCommanderPanel.getList())) {
          final SpeedSearchSupply supply = SpeedSearchBase.getSupply(myCommanderPanel.getList());
          if (supply != null && supply.isPopupActive()) supply.refreshSelection();
        }
      }
    });
    chkFilter.setFocusable(false);

    if (shortcuts.length > 0) {
      text += " (" + KeymapUtil.getShortcutText(shortcuts [0]) + ")";
      new AnAction() {
        public void actionPerformed(final AnActionEvent e) {
          chkFilter.doClick();
        }
      }.registerCustomShortcutSet(new CustomShortcutSet(shortcuts), myCommanderPanel);
    }
    chkFilter.setText(text);
    panel.add(chkFilter);
      //,new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 5, 0, 5), 0, 0));
  }

  @Nullable
  protected JComponent createSouthPanel() {
    return null;
  }

  public CommanderPanel getPanel() {
    return myCommanderPanel;
  }

  private class MyCommanderPanel extends CommanderPanel implements DataProvider {
    @Override
    protected boolean shouldDrillDownOnEmptyElement(final AbstractTreeNode node) {
      return false;
    }

    public MyCommanderPanel(Project _project) {
      super(_project, false, true);
      myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      myListSpeedSearch.addChangeListener(new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
          ProjectListBuilder builder = (ProjectListBuilder)getBuilder();
          if (builder == null) {
            return;
          }
          builder.addUpdateRequest(hasPrefixShortened(evt));
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              int index = myList.getSelectedIndex();
              if (index != -1 && index < myList.getModel().getSize()) {
                myList.clearSelection();
                ListScrollingUtil.selectItem(myList, index);
              }
              else {
                ListScrollingUtil.ensureSelectionExists(myList);
              }
            }
          });

          myList.repaint(); // to update match highlighting
        }
      });
      myListSpeedSearch.setComparator(createSpeedSearchComparator());
    }

    private boolean hasPrefixShortened(final PropertyChangeEvent evt) {
      return evt.getNewValue() != null && evt.getOldValue() != null &&
             ((String)evt.getNewValue()).length() < ((String)evt.getOldValue()).length();
    }

    public boolean navigateSelectedElement() {
      final Ref<Boolean> succeeded = new Ref<Boolean>();
      final CommandProcessor commandProcessor = CommandProcessor.getInstance();
      commandProcessor.executeCommand(myProject, new Runnable() {
        public void run() {
          succeeded.set(MyCommanderPanel.super.navigateSelectedElement());
          IdeDocumentHistory.getInstance(myProject).includeCurrentCommandAsNavigation();
        }
      }, "Navigate", null);
      if (succeeded.get()) {
        close(CANCEL_EXIT_CODE);
      }
      return succeeded.get();
    }

    public Object getData(String dataId) {
      Object selectedElement = myCommanderPanel.getSelectedValue();

      if (selectedElement instanceof TreeElement) selectedElement = ((StructureViewTreeElement)selectedElement).getValue();

      if (PlatformDataKeys.NAVIGATABLE.is(dataId)) {
        return selectedElement instanceof Navigatable ? selectedElement : myNavigatable;
      }

      if (OpenFileDescriptor.NAVIGATE_IN_EDITOR.is(dataId)) return myEditor;

      return getDataImpl(dataId);
    }

    public String getEnteredPrefix() {
      return myListSpeedSearch.getEnteredPrefix();
    }

    public void updateSpeedSearch() {
      myListSpeedSearch.refreshSelection();
    }

    public void scrollSelectionInView() {
      int selectedIndex = myList.getSelectedIndex();
      if (selectedIndex >= 0) {
        ListScrollingUtil.ensureIndexIsVisible(myList, selectedIndex, 0);
      }
    }
  }

  private class MyStructureTreeStructure extends SmartTreeStructure {
    public MyStructureTreeStructure() {
      super(FileStructureDialog.this.myProject, myTreeModel);
    }

    public Object[] getChildElements(Object element) {
      Object[] childElements = super.getChildElements(element);

      if (!myShouldNarrowDown) {
        return childElements;
      }

      String enteredPrefix = myCommanderPanel.getEnteredPrefix();
      if (enteredPrefix == null) {
        return childElements;
      }

      ArrayList<Object> filteredElements = new ArrayList<Object>(childElements.length);
      SpeedSearchBase.SpeedSearchComparator speedSearchComparator = createSpeedSearchComparator();

      for (Object child : childElements) {
        if (child instanceof AbstractTreeNode) {
          Object value = ((AbstractTreeNode)child).getValue();
          if (value instanceof TreeElement) {
            String name = ((TreeElement)value).getPresentation().getPresentableText();
            if (name == null) {
              continue;
            }
            if (speedSearchComparator.matchingFragments(enteredPrefix, name) == null) {
              continue;
            }
          }
        }
        filteredElements.add(child);
      }
      return ArrayUtil.toObjectArray(filteredElements);
    }

    public void rebuildTree() {
      getChildElements(getRootElement());   // for some reason necessary to rebuild tree correctly
      super.rebuildTree();
    }
  }

  private static SpeedSearchBase.SpeedSearchComparator createSpeedSearchComparator() {
    return new SpeedSearchBase.SpeedSearchComparator(false);
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
}
