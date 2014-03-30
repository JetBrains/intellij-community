/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.DataManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.structureView.ModelListener;
import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.StructureViewComposite;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.ide.structureView.newStructureView.TreeModelWrapper;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.ide.util.treeView.smartTree.*;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.LocationPresentation;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.*;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.PopupUpdateProcessor;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.treeStructure.AlwaysExpandedTree;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.filtered.FilteringTreeBuilder;
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class FileStructurePopup implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.FileStructurePopup");
  private final Project myProject;
  private final StructureViewModel myTreeModel;
  private final StructureViewModel myBaseTreeModel;
  private final TreeStructureActionsOwner myTreeActionsOwner;
  private PsiFile myPsiFile;
  private JBPopup myPopup;

  @NonNls private static final String narrowDownPropertyKey = "FileStructurePopup.narrowDown";
  private boolean myShouldNarrowDown = true;
  private final FileStructureTree myTree;
  private final FilteringTreeBuilder myAbstractTreeBuilder;
  private String myTitle;
  private final TreeSpeedSearch mySpeedSearch;
  private final SmartTreeStructure myTreeStructure;
  private int myPreferredWidth;
  private final FilteringTreeStructure myFilteringStructure;
  private final PsiElement myInitialPsiElement;
  private final Map<Class, JCheckBox> myCheckBoxes = new HashMap<Class, JCheckBox>();
  private final List<JCheckBox> myAutoClicked = new ArrayList<JCheckBox>();
  private String myTestSearchFilter;
  private final ActionCallback myTreeHasBuilt = new ActionCallback();
  private boolean myInitialNodeIsLeaf;
  private final List<Pair<String, JCheckBox>> myTriggeredCheckboxes = new ArrayList<Pair<String, JCheckBox>>();
  private final TreeExpander myTreeExpander;
  @NotNull private final FileEditor myFileEditor;
  private final StructureView myStructureViewDelegate;


  public FileStructurePopup(@NotNull Project project,
                            @NotNull FileEditor fileEditor,
                            @NotNull StructureView structureView,
                            final boolean applySortAndFilter) {
    myProject = project;
    myFileEditor = fileEditor;
    myStructureViewDelegate = structureView;

    //Stop code analyzer to speedup EDT
    DaemonCodeAnalyzer.getInstance(myProject).disableUpdateByTimer(this);
    IdeFocusManager.getInstance(myProject).typeAheadUntil(myTreeHasBuilt);
    Disposer.register(this, myStructureViewDelegate);

    //long l = System.currentTimeMillis();
    if (myFileEditor instanceof TextEditor) {
      Editor e = ((TextEditor)myFileEditor).getEditor();
      myPsiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(e.getDocument());
    }

    //System.out.println(System.currentTimeMillis() - l);
    if (myStructureViewDelegate instanceof StructureViewComposite) {
      StructureViewComposite.StructureViewDescriptor[] views = ((StructureViewComposite)myStructureViewDelegate).getStructureViews();
      myBaseTreeModel = new StructureViewCompositeModel(myPsiFile, views);
      Disposer.register(this, (Disposable)myBaseTreeModel);
    }
    else {
      myBaseTreeModel = myStructureViewDelegate.getTreeModel();
    }

    if (applySortAndFilter) {
      myTreeActionsOwner = new TreeStructureActionsOwner(myBaseTreeModel);
      myTreeModel = new TreeModelWrapper(myBaseTreeModel, myTreeActionsOwner);
    }
    else {
      myTreeActionsOwner = null;
      myTreeModel = myStructureViewDelegate.getTreeModel();
    }

    myTreeStructure = new SmartTreeStructure(project, myTreeModel) {
      @Override
      public void rebuildTree() {
        if (ApplicationManager.getApplication().isUnitTestMode() || !myPopup.isDisposed()) {
          super.rebuildTree();
        }
      }

      @Override
      public boolean isToBuildChildrenInBackground(final Object element) {
        return getRootElement() == element;
      }

      @Override
      protected TreeElementWrapper createTree() {
        return new StructureViewComponent.StructureViewTreeElementWrapper(myProject, myModel.getRoot(), myModel);
      }

      @NonNls
      @Override
      public String toString() {
        return "structure view tree structure(model=" + myTreeModel + ")";
      }
    };

    myTree = new FileStructureTree(myTreeStructure.getRootElement(), Registry.is("fast.tree.expand.in.structure.view"));

    myTree.setCellRenderer(new NodeRenderer());

    mySpeedSearch = new MyTreeSpeedSearch();
    mySpeedSearch.setComparator(new SpeedSearchComparator(false, true));

    final FileStructurePopupFilter filter = new FileStructurePopupFilter();
    myFilteringStructure = new FilteringTreeStructure(filter, myTreeStructure, ApplicationManager.getApplication().isUnitTestMode());
    myAbstractTreeBuilder = new FilteringTreeBuilder(myTree, filter, myFilteringStructure, null) {
      @Override
      public void initRootNode() {

      }

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

    myTreeExpander = new DefaultTreeExpander(myTree);
    final ModelListener modelListener = new ModelListener() {
      @Override
      public void onModelChanged() {
        myAbstractTreeBuilder.queueUpdate();
      }
    };
    myTreeModel.addModelListener(modelListener);
    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        myTreeModel.removeModelListener(modelListener);
      }
    });

    //myAbstractTreeBuilder.getUi().setPassthroughMode(true);
    myAbstractTreeBuilder.getUi().getUpdater().setDelay(1);
    myInitialPsiElement = getCurrentElement(myPsiFile);
    //myAbstractTreeBuilder.setCanYieldUpdate(true);
    Disposer.register(this, myAbstractTreeBuilder);
    TreeUtil.installActions(myTree);
  }

  public void show() {
    //final long time = System.currentTimeMillis();
    JComponent panel = createCenterPanel();
    new MnemonicHelper().register(panel);
    boolean shouldSetWidth = DimensionService.getInstance().getSize(getDimensionServiceKey(), myProject) == null;
    myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, null)
      .setTitle(myTitle)
      .setResizable(true)
      .setModalContext(false)
      .setFocusable(true)
      .setRequestFocus(true)
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

    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        if (myPopup.isVisible()) {
          final PopupUpdateProcessor updateProcessor = myPopup.getUserData(PopupUpdateProcessor.class);
          if (updateProcessor != null) {
            final AbstractTreeNode node = getSelectedNode();
            updateProcessor.updatePopup(node);
          }
        }
      }
    });
    Disposer.register(myPopup, this);
    Disposer.register(myPopup, new Disposable() {
      @Override
      public void dispose() {
        if (!myTreeHasBuilt.isDone()) {
          myTreeHasBuilt.setRejected();
        }
      }
    });
    myTree.getEmptyText().setText("Loading...");
    final Point location = DimensionService.getInstance().getLocation(getDimensionServiceKey(), myProject);
    if (location != null) {
      myPopup.showInScreenCoordinates(myFileEditor.getComponent(), location);
    }
    else {
      myPopup.showCenteredInCurrentWindow(myProject);
    }

    ((AbstractPopup)myPopup).setShowHints(true);
    if (shouldSetWidth) {
      myPopup.setSize(new Dimension(myPreferredWidth + 10, myPopup.getSize().height));
    }

    IdeFocusManager.getInstance(myProject).requestFocus(myTree, true);
    SwingUtilities.windowForComponent(myPopup.getContent()).addWindowFocusListener(new WindowFocusListener() {
      @Override
      public void windowGainedFocus(WindowEvent e) {
      }

      @Override
      public void windowLostFocus(WindowEvent e) {
        myPopup.cancel();
      }
    });
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            myFilteringStructure.rebuild();
          }
        });

        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            myAbstractTreeBuilder.queueUpdate().doWhenDone(new Runnable() {
              @Override
              public void run() {
                myTreeHasBuilt.setDone();
                //noinspection SSBasedInspection
                SwingUtilities.invokeLater(new Runnable() {
                  @Override
                  public void run() {
                    if (myAbstractTreeBuilder.isDisposed()) return;
                    if (selectPsiElement(myInitialPsiElement) == null) {
                      TreeUtil.ensureSelection(myAbstractTreeBuilder.getTree());
                      myAbstractTreeBuilder.revalidateTree();
                    }
                  }
                });
              }
            });
            installUpdater();
          }
        });
      }
    });
  }

  private void installUpdater() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }
    final Alarm alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, myPopup);
    alarm.addRequest(new Runnable() {
      String filter = "";

      @Override
      public void run() {
        alarm.cancelAllRequests();
        String prefix = mySpeedSearch.getEnteredPrefix();
        myTree.getEmptyText().setText(StringUtil.isEmpty(prefix) ? "Nothing to show" : "Can't find '" + prefix + "'");
        if (prefix == null) prefix = "";

        if (!filter.equals(prefix)) {
          final boolean isBackspace = prefix.length() < filter.length();
          filter = prefix;
          myAbstractTreeBuilder.refilter(null, false, false).doWhenProcessed(new Runnable() {
            @Override
            public void run() {
              //noinspection SSBasedInspection
              SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                  if (myAbstractTreeBuilder.isDisposed()) return;
                  myTree.repaint();
                  if (isBackspace && handleBackspace(filter)) {
                    return;
                  }
                  if (myFilteringStructure.getRootElement().getChildren().length == 0) {
                    for (JCheckBox box : myCheckBoxes.values()) {
                      if (!box.isSelected()) {
                        myAutoClicked.add(box);
                        myTriggeredCheckboxes.add(0, Pair.create(filter, box));
                        box.doClick();
                        filter = "";
                        break;
                      }
                    }
                  }
                }
              });
            }
          });
        }
        if (!alarm.isDisposed()) {
          alarm.addRequest(this, 300);
        }
      }
    }, 300);
  }

  private boolean handleBackspace(String filter) {
    boolean clicked = false;
    final Iterator<Pair<String, JCheckBox>> iterator = myTriggeredCheckboxes.iterator();
    while (iterator.hasNext()) {
      final Pair<String, JCheckBox> next = iterator.next();
      if (next.getFirst().length() < filter.length()) break;

      if (next.getFirst().length() >= filter.length()) {
        iterator.remove();
        next.getSecond().doClick();
        clicked = true;
      }
    }
    return clicked;
  }

  @Nullable
  public FilteringTreeStructure.FilteringNode selectPsiElement(PsiElement element) {
    Set<PsiElement> parents = getAllParents(element);

    FilteringTreeStructure.FilteringNode node = (FilteringTreeStructure.FilteringNode)myAbstractTreeBuilder.getRootElement();
    if (element != null && node != null && myStructureViewDelegate instanceof StructureViewComposite) {
      parents.remove(element.getContainingFile());
      final List<FilteringTreeStructure.FilteringNode> fileNodes = node.children();

      for (FilteringTreeStructure.FilteringNode fileNode : fileNodes) {
        final FilteringTreeStructure.FilteringNode found = findNode(parents, fileNode);
        if (found != null && found != fileNode) {
          return found;
        }
      }
    }
    else {
      final FilteringTreeStructure.FilteringNode found = findNode(parents, node);
      if (found == null) {
        TreeUtil.ensureSelection(myTree);
      }
      return found;
    }
    TreeUtil.ensureSelection(myTree);
    return null;
  }

  private FilteringTreeStructure.FilteringNode findNode(Set<PsiElement> parents, FilteringTreeStructure.FilteringNode node) {
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
        myAbstractTreeBuilder.select(node);
        if (myAbstractTreeBuilder.getSelectedElements().isEmpty()) {
          TreeUtil.selectFirstNode(myTree);
        }
        myInitialNodeIsLeaf = node.getChildren().length == 0;
        return node;
      }
    }
    return null;
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

  @Override
  public void dispose() {

  }

  @NonNls
  protected static String getDimensionServiceKey() {
    return "StructurePopup";
  }

  @Nullable
  public PsiElement getCurrentElement(@Nullable final PsiFile psiFile) {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    Object elementAtCursor = myTreeModel.getCurrentEditorElement();
    if (elementAtCursor instanceof PsiElement) {
      return (PsiElement)elementAtCursor;
    }

    if (psiFile != null && myFileEditor instanceof TextEditor) {
      return psiFile.getViewProvider().findElementAt(((TextEditor)myFileEditor).getEditor().getCaretModel().getOffset());
    }

    return null;
  }

  public JComponent createCenterPanel() {
    List<FileStructureFilter> fileStructureFilters = new ArrayList<FileStructureFilter>();
    List<FileStructureNodeProvider> fileStructureNodeProviders = new ArrayList<FileStructureNodeProvider>();
    if (myTreeActionsOwner != null) {
      for (Filter filter : myBaseTreeModel.getFilters()) {
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
      @Override
      public void actionPerformed(AnActionEvent e) {
        final boolean succeeded = navigateSelectedElement();
        if (succeeded) {
          unregisterCustomShortcutSet(panel);
        }
      }
    }.registerCustomShortcutSet(shortcutSet, panel);

    new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        if (mySpeedSearch != null && mySpeedSearch.isPopupActive()) {
          mySpeedSearch.hidePopup();
        }
        else {
          myPopup.cancel();
        }
      }
    }.registerCustomShortcutSet(CustomShortcutSet.fromString("ESCAPE"), myTree);

    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        final TreePath path = myTree.getPathForLocation(e.getX(), e.getY());
        if (path == null) return false; // user wants to expand/collapse a node
        navigateSelectedElement();
        return true;
      }
    }.installOn(myTree);

    for (FileStructureFilter filter : fileStructureFilters) {
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
        if (CommonDataKeys.PROJECT.is(dataId)) {
          return myProject;
        }
        if (PlatformDataKeys.FILE_EDITOR.is(dataId)) {
          return myFileEditor;
        }
        if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
          Object node = ContainerUtil.getFirstItem(myAbstractTreeBuilder.getSelectedElements());
          if (!(node instanceof FilteringTreeStructure.FilteringNode)) return null;
          return getPsi((FilteringTreeStructure.FilteringNode)node);
        }
        if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
          Set<Object> nodes = myAbstractTreeBuilder.getSelectedElements();
          if (nodes.isEmpty()) return PsiElement.EMPTY_ARRAY;
          ArrayList<PsiElement> result = new ArrayList<PsiElement>();
          for (Object o : nodes) {
            if (!(o instanceof FilteringTreeStructure.FilteringNode)) continue;
            ContainerUtil.addIfNotNull(result, getPsi((FilteringTreeStructure.FilteringNode)o));
          }
          return ContainerUtil.toArray(result, PsiElement.ARRAY_FACTORY);
        }
        if (LangDataKeys.POSITION_ADJUSTER_POPUP.is(dataId)) {
          return myPopup;
        }
        if (PlatformDataKeys.TREE_EXPANDER.is(dataId)) {
          return myTreeExpander;
        }
        return null;
      }
    });

    panel.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        myPopup.cancel();
      }
    });

    return panel;
  }

  @Nullable
  private AbstractTreeNode getSelectedNode() {
    final TreePath path = myTree.getSelectionPath();
    if (path != null) {
      Object component = path.getLastPathComponent();
      if (component instanceof DefaultMutableTreeNode) {
        component = ((DefaultMutableTreeNode)component).getUserObject();
        if (component instanceof FilteringTreeStructure.FilteringNode) {
          component = ((FilteringTreeStructure.FilteringNode)component).getDelegate();
          if (component instanceof AbstractTreeNode) {
            return (AbstractTreeNode)component;
          }
        }
      }
    }
    return null;
  }

  public boolean navigateSelectedElement() {
    final AbstractTreeNode selectedNode = getSelectedNode();
    if (ApplicationManager.getApplication().isInternal()) {
      String enteredPrefix = getSpeedSearch().getEnteredPrefix();
      String itemText = getSpeedSearchText(selectedNode);
      if (StringUtil.isNotEmpty(enteredPrefix) && StringUtil.isNotEmpty(itemText)) {
        LOG.info("Chosen in file structure popup by prefix '" + enteredPrefix + "': '" + itemText + "'");
      }
    }

    final Ref<Boolean> succeeded = new Ref<Boolean>();
    final CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(myProject, new Runnable() {
      @Override
      public void run() {
        if (selectedNode != null) {
          if (selectedNode.canNavigateToSource()) {
            myPopup.cancel();
            selectedNode.navigate(true);
            succeeded.set(true);
          }
          else {
            succeeded.set(false);
          }
        }
        else {
          succeeded.set(false);
        }

        IdeDocumentHistory.getInstance(myProject).includeCurrentCommandAsNavigation();
      }
    }, "Navigate", null);
    return succeeded.get();
  }

  private JComponent createSouthPanel() {
    final JCheckBox checkBox = new JCheckBox(IdeBundle.message("checkbox.narrow.down.on.typing"));
    checkBox.setSelected(PropertiesComponent.getInstance().getBoolean(narrowDownPropertyKey, true));
    checkBox.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        myShouldNarrowDown = checkBox.isSelected();
        PropertiesComponent.getInstance().setValue(narrowDownPropertyKey, Boolean.toString(myShouldNarrowDown));

        if (mySpeedSearch.isPopupActive() && !StringUtil.isEmpty(mySpeedSearch.getEnteredPrefix())) {
          myAbstractTreeBuilder.queueUpdate();
        }
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

    Shortcut[] shortcuts = extractShortcutFor(action);


    final JCheckBox chkFilter = new JCheckBox();
    UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, chkFilter);

    final boolean selected = getDefaultValue(action);
    chkFilter.setSelected(selected);
    myTreeActionsOwner.setActionIncluded(action, action instanceof FileStructureFilter ? !selected : selected);
    chkFilter.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        final boolean state = chkFilter.isSelected();
        if (!myAutoClicked.contains(chkFilter)) {
          saveState(action, state);
        }
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
        final Runnable runnable = new Runnable() {
          @Override
          public void run() {
            ApplicationManager.getApplication().runReadAction(new Runnable() {
              @Override
              public void run() {
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
          }
        };
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          runnable.run();
        }
        else {
          ApplicationManager.getApplication().invokeLater(runnable);
        }
      }
    });
    chkFilter.setFocusable(false);

    if (shortcuts.length > 0) {
      text += " (" + KeymapUtil.getShortcutText(shortcuts[0]) + ")";
      new AnAction() {
        @Override
        public void actionPerformed(final AnActionEvent e) {
          chkFilter.doClick();
        }
      }.registerCustomShortcutSet(new CustomShortcutSet(shortcuts), myTree);
    }
    chkFilter.setText(text);
    panel.add(chkFilter);
    myCheckBoxes.put(action.getClass(), chkFilter);
  }

  @NotNull
  static Shortcut[] extractShortcutFor(@NotNull TreeAction action) {
    if (action instanceof ActionShortcutProvider) {
      String actionId = ((ActionShortcutProvider)action).getActionIdForShortcut();
      return KeymapManager.getInstance().getActiveKeymap().getShortcuts(actionId);
    }
    return action instanceof FileStructureFilter ?
                           ((FileStructureFilter)action).getShortcut() : ((FileStructureNodeProvider)action).getShortcut();
  }

  private static boolean getDefaultValue(TreeAction action) {
    if (action instanceof PropertyOwner) {
      final String propertyName = ((PropertyOwner)action).getPropertyName();
      return PropertiesComponent.getInstance().getBoolean(TreeStructureUtil.getPropertyName(propertyName), false);
    }

    return false;
  }

  private static void saveState(TreeAction action, boolean state) {
    if (action instanceof PropertyOwner) {
      final String propertyName = ((PropertyOwner)action).getPropertyName();
      PropertiesComponent.getInstance().setValue(TreeStructureUtil.getPropertyName(propertyName), Boolean.toString(state));
    }
  }

  public void setTitle(String title) {
    myTitle = title;
  }

  public Tree getTree() {
    return myTree;
  }

  public TreeSpeedSearch getSpeedSearch() {
    return mySpeedSearch;
  }

  public FilteringTreeBuilder getTreeBuilder() {
    return myAbstractTreeBuilder;
  }

  public void setSearchFilterForTests(String filter) {
    myTestSearchFilter = filter;
  }

  public void setTreeActionState(Class<? extends TreeAction> action, boolean state) {
    final JCheckBox checkBox = myCheckBoxes.get(action);
    if (checkBox != null) {
      checkBox.setSelected(state);
      for (ActionListener listener : checkBox.getActionListeners()) {
        listener.actionPerformed(new ActionEvent(this, 1, ""));
      }
    }
  }

  @Nullable
  public static String getSpeedSearchText(final Object userObject) {
    String text = String.valueOf(userObject);
    if (text != null) {
      if (userObject instanceof StructureViewComponent.StructureViewTreeElementWrapper) {
        final TreeElement value = ((StructureViewComponent.StructureViewTreeElementWrapper)userObject).getValue();
        if (value instanceof PsiTreeElementBase && ((PsiTreeElementBase)value).isSearchInLocationString()) {
          final String locationString = ((PsiTreeElementBase)value).getLocationString();
          if (!StringUtil.isEmpty(locationString)) {
            String locationPrefix = null;
            String locationSuffix = null;
            if (value instanceof LocationPresentation) {
              locationPrefix = ((LocationPresentation)value).getLocationPrefix();
              locationSuffix = ((LocationPresentation)value).getLocationSuffix();
            }

            return text +
                   StringUtil.notNullize(locationPrefix, LocationPresentation.DEFAULT_LOCATION_PREFIX) +
                   locationString +
                   StringUtil.notNullize(locationSuffix, LocationPresentation.DEFAULT_LOCATION_SUFFIX);
          }
        }
      }
      return text;
    }

    if (userObject instanceof StructureViewComponent.StructureViewTreeElementWrapper) {
      return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        @Nullable
        @Override
        public String compute() {
          final ItemPresentation presentation =
            ((StructureViewComponent.StructureViewTreeElementWrapper)userObject).getValue().getPresentation();
          return presentation.getPresentableText();
        }
      });
    }

    return null;
  }

  private class FileStructurePopupFilter implements ElementFilter {
    private String myLastFilter = null;
    private final Set<Object> myVisibleParents = new HashSet<Object>();
    private final boolean isUnitTest = ApplicationManager.getApplication().isUnitTestMode();

    @Override
    public boolean shouldBeShowing(Object value) {
      if (!myShouldNarrowDown) return true;

      String filter = getSearchPrefix();
      if (!StringUtil.equals(myLastFilter, filter)) {
        myVisibleParents.clear();
        myLastFilter = filter;
      }
      if (filter != null) {
        if (myVisibleParents.contains(value)) {
          return true;
        }

        final String text = getSpeedSearchText(value);
        if (text == null) return false;

        if (matches(text)) {
          Object o = value;
          while (o instanceof FilteringTreeStructure.FilteringNode && (o = ((FilteringTreeStructure.FilteringNode)o).getParent()) != null) {
            myVisibleParents.add(o);
          }
          return true;
        }
        else {
          return false;
        }
      }
      return true;
    }

    private boolean matches(@NotNull String text) {
      if (isUnitTest) {
        final SpeedSearchComparator comparator = mySpeedSearch.getComparator();
        return StringUtil.isNotEmpty(myTestSearchFilter) && comparator.matchingFragments(myTestSearchFilter, text) != null;
      }
      return mySpeedSearch.matchingFragments(text) != null;
    }
  }

  @Nullable
  private String getSearchPrefix() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return myTestSearchFilter;

    return mySpeedSearch != null && !StringUtil.isEmpty(mySpeedSearch.getEnteredPrefix())
           ? mySpeedSearch.getEnteredPrefix() : null;
  }

  public class MyTreeSpeedSearch extends TreeSpeedSearch {
    public MyTreeSpeedSearch() {
      super(myTree, new Convertor<TreePath, String>() {
        @Override
        @Nullable
        public String convert(TreePath path) {
          final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
          final Object userObject = node.getUserObject();
          if (userObject instanceof FilteringTreeStructure.FilteringNode) {
            return getSpeedSearchText(((FilteringTreeStructure.FilteringNode)userObject).getDelegate());
          }
          return "";
        }
      }, true);
    }

    @Override
    protected Point getComponentLocationOnScreen() {
      return myPopup.getContent().getLocationOnScreen();
    }

    @Override
    protected Rectangle getComponentVisibleRect() {
      return myPopup.getContent().getVisibleRect();
    }

    @Override
    public Object findElement(String s) {
      final List<SpeedSearchObjectWithWeight> elements = SpeedSearchObjectWithWeight.findElement(s, this);
      return elements.isEmpty() ? null : findClosestTo(myInitialPsiElement, elements);
    }

    @Nullable
    private Object findClosestTo(PsiElement path, List<SpeedSearchObjectWithWeight> paths) {
      if (path == null || myInitialPsiElement == null) {
        return paths.get(0).node;
      }
      final Set<PsiElement> parents = getAllParents(myInitialPsiElement);
      ArrayList<SpeedSearchObjectWithWeight> cur = new ArrayList<SpeedSearchObjectWithWeight>();
      int max = -1;
      for (SpeedSearchObjectWithWeight p : paths) {
        final Object last = ((TreePath)p.node).getLastPathComponent();
        final List<PsiElement> elements = new ArrayList<PsiElement>();
        final Object object = ((DefaultMutableTreeNode)last).getUserObject();
        if (object instanceof FilteringTreeStructure.FilteringNode) {
          FilteringTreeStructure.FilteringNode node = (FilteringTreeStructure.FilteringNode)object;
          FilteringTreeStructure.FilteringNode candidate = node;

          while (node != null) {
            elements.add(getPsi(node));
            node = node.getParentNode();
          }
          final int size = ContainerUtil.intersection(parents, elements).size();
          if (size == elements.size() - 1 && size == parents.size() - (myInitialNodeIsLeaf ? 1 : 0) && candidate.children().isEmpty()) {
            return p.node;
          }
          if (size > max) {
            max = size;
            cur.clear();
            cur.add(p);
          }
          else if (size == max) {
            cur.add(p);
          }
        }
      }

      Collections.sort(cur, new Comparator<SpeedSearchObjectWithWeight>() {
        @Override
        public int compare(SpeedSearchObjectWithWeight o1, SpeedSearchObjectWithWeight o2) {
          final int i = o1.compareWith(o2);
          return i != 0 ? i
                        : ((TreePath)o2.node).getPathCount() - ((TreePath)o1.node).getPathCount();
        }
      });
      return cur.isEmpty() ? null : cur.get(0).node;
    }
  }

  class FileStructureTree extends JBTreeWithHintProvider implements AlwaysExpandedTree {
    private final boolean fast;

    public FileStructureTree(Object rootElement, boolean fastExpand) {
      super(new DefaultMutableTreeNode(rootElement));
      if (fastExpand) {
        boolean newValueIsSet;
        try {
          final Field field = JTree.class.getDeclaredField("expandedState");
          field.setAccessible(true);
          field.set(this, new Hashtable() {
            @Override
            public synchronized Object get(Object key) {
              return Boolean.TRUE;
            }
          });
          newValueIsSet = true;
        }
        catch (Exception e) {
          newValueIsSet = false;
        }
        fast = newValueIsSet;
      }
      else {
        fast = false;
      }

      //TODO[kb]: hack expanded states in getUI().treeState

      setRootVisible(false);
      setShowsRootHandles(true);
      setHorizontalAutoScrollingEnabled(false);
    }

    @Override
    public boolean isAlwaysExpanded() {
      return fast;
    }

    @Override
    public boolean isExpanded(TreePath path) {
      return fast || super.isExpanded(path);
    }

    @Override
    public boolean isExpanded(int row) {
      return fast || super.isExpanded(row);
    }

    @Override
    protected PsiElement getPsiElementForHint(Object selectedValue) {
      //noinspection ConstantConditions
      return getPsi((FilteringTreeStructure.FilteringNode)((DefaultMutableTreeNode)selectedValue).getUserObject());
    }
  }
}
