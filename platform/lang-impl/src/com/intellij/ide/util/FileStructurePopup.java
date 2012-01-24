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

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.ide.structureView.newStructureView.TreeModelWrapper;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.ide.util.treeView.smartTree.*;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.*;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.filtered.FilteringTreeBuilder;
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class FileStructurePopup implements Disposable {
  private static final Comparator<TextRange> TEXT_RANGE_COMPARATOR = new Comparator<TextRange>() {
    @Override
    public int compare(TextRange o1, TextRange o2) {
      if (o1.getStartOffset() == o2.getStartOffset()) {
        return o2.getEndOffset() - o1.getEndOffset(); //longer is better
      }
      return o1.getStartOffset() - o2.getStartOffset();
    }
  };
  private final Editor myEditor;
  private final Project myProject;
  private final StructureViewModel myTreeModel;
  private final StructureViewModel myBaseTreeModel;
  private final TreeStructureActionsOwner myTreeActionsOwner;
  private JBPopup myPopup;

  @NonNls private static final String narrowDownPropertyKey = "FileStructurePopup.narrowDown";
  private boolean myShouldNarrowDown = false;
  private Tree myTree;
  private FilteringTreeBuilder myAbstractTreeBuilder;
  private String myTitle;
  private TreeSpeedSearch mySpeedSearch;
  private SmartTreeStructure myTreeStructure;
  private int myPreferredWidth;
  private final FilteringTreeStructure myFilteringStructure;
  private PsiElement myInitialPsiElement;

  public FileStructurePopup(StructureViewModel structureViewModel,
                            @Nullable Editor editor,
                            Project project,
                            @NotNull final Disposable auxDisposable,
                            final boolean applySortAndFilter) {
    myProject = project;
    myEditor = editor;
    myBaseTreeModel = structureViewModel;
    Disposer.register(this, auxDisposable);
    if (applySortAndFilter) {
      myTreeActionsOwner = new TreeStructureActionsOwner(myBaseTreeModel);
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
    myTree.setCellRenderer(new NodeRenderer(){
      @Override
      protected void doAppend(@NotNull @Nls String fragment, @NotNull SimpleTextAttributes attributes, boolean isMainText, boolean selected) {
        SpeedSearchUtil.appendFragmentsForSpeedSearch(myTree, fragment, attributes, selected, this);
      }

      @Override
      public void doAppend(@NotNull String fragment, @NotNull SimpleTextAttributes attributes, boolean selected) {
        SpeedSearchUtil.appendFragmentsForSpeedSearch(myTree, fragment, attributes, selected, this);
      }

      @Override
      public void doAppend(String fragment, boolean selected) {
        SpeedSearchUtil.appendFragmentsForSpeedSearch(myTree, fragment, SimpleTextAttributes.REGULAR_ATTRIBUTES, selected, this);
      }
    });
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

      @Override
      protected Object findElement(String s) {
        List<ObjectWithWeight> elements = new ArrayList<ObjectWithWeight>();
        s = s.trim();
        final ListIterator<Object> it = getElementIterator(0);
        while (it.hasNext()) {
          final ObjectWithWeight o = new ObjectWithWeight(it.next(), s, getComparator());
          if (!o.weights.isEmpty()) {
            elements.add(o);
          }
        }
        ObjectWithWeight cur = null;
        ArrayList<ObjectWithWeight> current = new ArrayList<ObjectWithWeight>();
        for (ObjectWithWeight element : elements) {
          if (cur == null) {
            cur = element;
            current.add(cur);
            continue;
          }

          final int i = element.compareWith(cur);
          if (i == 0) {
            current.add(element);
          } else if (i < 0) {
            cur = element;
            current.clear();
            current.add(cur);
          }
        }

        return current.isEmpty() ? null : findClosestTo(myInitialPsiElement, current);
      }

      private Object findClosestTo(PsiElement path, ArrayList<ObjectWithWeight> paths) {
        if (path == null || myInitialPsiElement == null) {
          return paths.get(0).node;
        }
        final Set<PsiElement> parents = getAllParents(myInitialPsiElement);
        Object cur = paths.get(0).node;
        int max = -1;
        for (ObjectWithWeight p : paths) {
          final Object last = ((TreePath)p.node).getLastPathComponent();
          final List<PsiElement> elements = new ArrayList<PsiElement>();
          FilteringTreeStructure.FilteringNode node =
            (FilteringTreeStructure.FilteringNode)((DefaultMutableTreeNode)last).getUserObject();
          while (node != null) {
            elements.add(getPsi(node));
            node = node.getParentNode();
          }
          final int size = ContainerUtil.intersection(parents, elements).size();
          if (size > max) {
            max = size;
            cur = p.node;
          }
        }

        return cur;
      }

      class ObjectWithWeight {
        final Object node;
        final List<TextRange> weights = new ArrayList<TextRange>();

        ObjectWithWeight(Object element, String pattern, SpeedSearchComparator comparator) {
          this.node = element;
          final String text = getElementText(element);
          if (text != null) {
            final Iterable<TextRange> ranges = comparator.matchingFragments(pattern, text);
            if (ranges != null) {
              for (TextRange range : ranges) {
                weights.add(range);
              }
            }
          }
          Collections.sort(weights, TEXT_RANGE_COMPARATOR);
        }
        
        int compareWith(ObjectWithWeight obj) {
          final List<TextRange> w = obj.weights;
          for (int i = 0; i < weights.size(); i++) {
            if (i >= w.size()) return 1;
            final int result = TEXT_RANGE_COMPARATOR.compare(weights.get(i), w.get(i));
            if (result != 0) {
              return result;
            }
          }
          
          return 0;
        }
        
      }
      
    };
    mySpeedSearch.setComparator(new SpeedSearchComparator(false, true));

    final FileStructurePopupFilter filter = new FileStructurePopupFilter();
    myFilteringStructure = new FilteringTreeStructure(filter, myTreeStructure, false);
    myAbstractTreeBuilder = new FilteringTreeBuilder(myTree, filter, myFilteringStructure, null) {
      @Override
      protected boolean validateNode(Object child) {
        return StructureViewComponent.isValid(child);
      }

      @Override
      public void revalidateTree() {
        //myTree.revalidate();
        //myTree.repaint();
      }

      @Override
      public boolean isToEnsureSelectionOnFocusGained() {
        return false;
      }
    };

    myAbstractTreeBuilder.getUi().getUpdater().setDelay(1);

    //myAbstractTreeBuilder.setCanYieldUpdate(true);
    Disposer.register(this, myAbstractTreeBuilder);
  }

  public void show() {
    //final long time = System.currentTimeMillis();
    final ActionCallback treeHasBuilt = new ActionCallback();
    IdeFocusManager.getInstance(myProject).typeAheadUntil(treeHasBuilt);
    JComponent panel = createCenterPanel();
    new MnemonicHelper().register(panel);
    boolean shouldSetWidth = DimensionService.getInstance().getSize(getDimensionServiceKey(), myProject) == null;
    myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, null)
      .setTitle(myTitle)
      .setResizable(true)
      .setModalContext(false)
      .setFocusable(true)
      .setMovable(true)
      .setBelongsToGlobalPopupStack(true)
      //.setCancelOnClickOutside(false) //for debug and snapshots
      .setCancelKeyEnabled(false)
      .setDimensionServiceKey(null, getDimensionServiceKey(), false)
      .setCancelCallback(new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          DimensionService.getInstance().setLocation(getDimensionServiceKey(), myPopup.getLocationOnScreen(), myProject);
          return true;
        }
      })
      .createPopup();
    Disposer.register(myPopup, this);
    Disposer.register(myPopup, new Disposable() {
      @Override
      public void dispose() {
        if (!treeHasBuilt.isDone()) {
          treeHasBuilt.setRejected();
        }
      }
    });
    final Point location = DimensionService.getInstance().getLocation(getDimensionServiceKey(), myProject);
    if (location != null) {
      myPopup.showInScreenCoordinates(myEditor.getContentComponent(), location);
    } else {
      myPopup.showCenteredInCurrentWindow(myProject);
    }

    ((AbstractPopup)myPopup).setShowHints(true);
    if (shouldSetWidth) {
      myPopup.setSize(new Dimension(myPreferredWidth + 10, myPopup.getSize().height));
    }
    myAbstractTreeBuilder.expandAll(new Runnable() {
      @Override
      public void run() {
        IdeFocusManager.getInstance(myProject).requestFocus(myTree, true);
        myAbstractTreeBuilder.queueUpdate().doWhenDone(new Runnable() {
          @Override
          public void run() {
            myInitialPsiElement = getCurrentElement(getPsiFile(myProject));
            selectPsiElement(myInitialPsiElement);
            treeHasBuilt.setDone();
            //long t = System.currentTimeMillis() - time;
            //System.out.println("Shown in " + t + "ms");
          }
        });
      }
    });
    final Alarm alarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD, myPopup);
    alarm.addRequest(new Runnable() {
      String filter = "";

      @Override
      public void run() {
        alarm.cancelAllRequests();
        String prefix = mySpeedSearch.getEnteredPrefix();
        myTree.getEmptyText().setText(StringUtil.isEmpty(prefix) ? "Nothing to show" : "Can't find '" + prefix + "'");
        if (prefix == null) prefix = "";

        if (!filter.equals(prefix)) {
          filter = prefix;
          myAbstractTreeBuilder.refilter(null, false, false).doWhenProcessed(new Runnable() {
            @Override
            public void run() {
              myTree.repaint();
              //if (mySpeedSearch.isPopupActive()) {
              //  mySpeedSearch.refreshSelection();
              //}
            }
          });
        }
        alarm.addRequest(this, 300);
      }
    }, 300);
  }

  private void selectPsiElement(PsiElement element) {
    Set<PsiElement> parents = getAllParents(element);

    FilteringTreeStructure.FilteringNode node = (FilteringTreeStructure.FilteringNode)myAbstractTreeBuilder.getRootElement();
    while (node != null) {
      boolean changed = false;
      for (FilteringTreeStructure.FilteringNode n : node.children()) {
        final PsiElement psiElement = getPsi(n);
        if (psiElement != null && parents.contains(psiElement)) {
          node = n;
          changed = true;
          break;
        }
      }
      if (!changed) {
        myAbstractTreeBuilder.getUi().select(node, null);
        if (myAbstractTreeBuilder.getSelectedElements().isEmpty()) {
          TreeUtil.selectFirstNode(myTree);
        }
        return;

      }
    }
    TreeUtil.selectFirstNode(myTree);
  }

  private static Set<PsiElement> getAllParents(PsiElement element) {
    Set<PsiElement> parents = new java.util.HashSet<PsiElement>();

    while (element != null) {
      parents.add(element);
      if (element instanceof PsiFile) break;
      element = element.getParent();
    }
    return parents;
  }

  @Nullable
  private PsiElement getPsi(FilteringTreeStructure.FilteringNode n) {
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
    
    myTree.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() > 1) {          
          navigateSelectedElement();
        }
      }
    });

    for(FileStructureFilter filter: fileStructureFilters) {
      addCheckbox(comboPanel, filter);
    }

    for (FileStructureNodeProvider provider : fileStructureNodeProviders) {
      addCheckbox(comboPanel, provider);
    }
    myPreferredWidth = Math.max(comboPanel.getPreferredSize().width, 350);
    panel.add(comboPanel, BorderLayout.NORTH);
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myAbstractTreeBuilder.getTree());
    scrollPane.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP | SideBorder.BOTTOM));
    panel.add(scrollPane, BorderLayout.CENTER);
    panel.add(createSouthPanel(), BorderLayout.SOUTH);
    DataManager.registerDataProvider(panel, new DataProvider() {
      @Override
      public Object getData(@NonNls String dataId) {
        if (PlatformDataKeys.PROJECT.is(dataId)) {
          return myProject;
        }
        if (LangDataKeys.PSI_ELEMENT.is(dataId)) {
          final Object node = ContainerUtil.getFirstItem(myAbstractTreeBuilder.getSelectedElements());
          if (!(node instanceof FilteringTreeStructure.FilteringNode)) return null;
          return getPsi((FilteringTreeStructure.FilteringNode)node);
        }
        return null;
      }
    });
    myFilteringStructure.rebuild();
    return panel;
  }

  @Nullable
  private AbstractTreeNode getSelectedNode() {
    Object component = myTree.getSelectionPath().getLastPathComponent();
    if (component instanceof DefaultMutableTreeNode) {
      component = ((DefaultMutableTreeNode)component).getUserObject();
      if (component instanceof FilteringTreeStructure.FilteringNode) {
        component = ((FilteringTreeStructure.FilteringNode)component).getDelegate();
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
    final JCheckBox checkBox = new JCheckBox(IdeBundle.message("checkbox.narrow.down.on.typing"));
    checkBox.setSelected(PropertiesComponent.getInstance().getBoolean(narrowDownPropertyKey, true));
    checkBox.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        myShouldNarrowDown = checkBox.isSelected();
        PropertiesComponent.getInstance().setValue(narrowDownPropertyKey, Boolean.toString(myShouldNarrowDown));

        myAbstractTreeBuilder.queueUpdate();
      }
    });

    checkBox.setFocusable(false);
    UIUtil.applyStyle(UIUtil.ComponentStyle.MINI, checkBox);
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
    final boolean selected = getDefaultValue(action);
    chkFilter.setSelected(selected);
    myTreeActionsOwner.setActionIncluded(action, action instanceof FileStructureFilter ? !selected : selected);
    chkFilter.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final boolean state = chkFilter.isSelected();
        saveState(action, state);
        myTreeActionsOwner.setActionIncluded(action, action instanceof FileStructureFilter ? !state : state);
        //final String filter = mySpeedSearch.isPopupActive() ? mySpeedSearch.getEnteredPrefix() : null;
        //mySpeedSearch.hidePopup();
        Object selection = ContainerUtil.getFirstItem(myAbstractTreeBuilder.getSelectedElements());
        if (selection instanceof FilteringTreeStructure.FilteringNode) {
          selection = ((FilteringTreeStructure.FilteringNode)selection).getDelegate();
        }
        myTreeStructure.rebuildTree();
        myFilteringStructure.rebuild();
        
        final Object sel = selection;
        myAbstractTreeBuilder.refilter(sel, true, false).doWhenProcessed(new Runnable() {
          @Override
          public void run() {
            if (mySpeedSearch.isPopupActive()) {
              mySpeedSearch.refreshSelection();
            }
          }
        });
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

  private static boolean getDefaultValue(TreeAction action) {
    if (action instanceof PropertyOwner) {
      final String propertyName = ((PropertyOwner)action).getPropertyName();
      return PropertiesComponent.getInstance().getBoolean(getPropertyName(propertyName), false);
    }

    return false;
  }

  private static void saveState(TreeAction action, boolean state) {
    if (action instanceof PropertyOwner) {
      final String propertyName = ((PropertyOwner)action).getPropertyName();
      PropertiesComponent.getInstance().setValue(getPropertyName(propertyName), Boolean.toString(state));
    }
  }

  private static String getPropertyName(String propertyName) {
    return propertyName + ".file.structure.state";
  }

  public void setTitle(String title) {
    myTitle = title;
  }

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

        final String text = getText(value);
        if (text == null) return false;
        boolean matches = mySpeedSearch.matchingFragments(text) != null;

        if (matches) {
          Object o = value;
          while (o instanceof FilteringTreeStructure.FilteringNode && (o = ((FilteringTreeStructure.FilteringNode)o).getParent()) != null) {
            myVisibleParents.add(o);
          }
          return true;
        } else {
          return false;
        }
        
      } 
      return true;
    }

    @Nullable
    private String getText(Object node) {
      final String text = String.valueOf(node);
      if (text != null) {
        return text;
      }

      if (node instanceof StructureViewComponent.StructureViewTreeElementWrapper) {
        final AccessToken token = ApplicationManager.getApplication().acquireReadActionLock();
        try {
          final ItemPresentation presentation = ((StructureViewComponent.StructureViewTreeElementWrapper)node).getValue().getPresentation();
          return presentation.getPresentableText();
        }
        finally {
          token.finish();
        }
      }

      return null;
    }
  }
}
