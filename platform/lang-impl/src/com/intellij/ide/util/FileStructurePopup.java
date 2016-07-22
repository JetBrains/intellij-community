/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.icons.AllIcons;
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
import com.intellij.ide.structureView.newStructureView.TreeActionWrapper;
import com.intellij.ide.structureView.newStructureView.TreeActionsOwner;
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
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.ui.*;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.PopupUpdateProcessor;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.treeStructure.AlwaysExpandedTree;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.filtered.FilteringTreeBuilder;
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.TextTransferable;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class FileStructurePopup implements Disposable, TreeActionsOwner {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.FileStructurePopup");
  private final Project myProject;
  private final StructureViewModel myTreeModel;
  private final StructureViewModel myBaseTreeModel;
  private final TreeStructureActionsOwner myTreeActionsOwner;
  private PsiFile myPsiFile;
  private JBPopup myPopup;

  @NonNls private static final String narrowDownPropertyKey = "FileStructurePopup.narrowDown";
  private final FileStructureTree myTree;
  private final FilteringTreeBuilder myAbstractTreeBuilder;
  private String myTitle;
  private final TreeSpeedSearch mySpeedSearch;
  private final SmartTreeStructure myTreeStructure;
  private int myPreferredWidth;
  private final FilteringTreeStructure myFilteringStructure;
  private final PsiElement myInitialPsiElement;
  private final Map<Class, JCheckBox> myCheckBoxes = new HashMap<>();
  private final List<JCheckBox> myAutoClicked = new ArrayList<>();
  private String myTestSearchFilter;
  private final ActionCallback myTreeHasBuilt = new ActionCallback();
  private boolean myInitialNodeIsLeaf;
  private final List<Pair<String, JCheckBox>> myTriggeredCheckboxes = new ArrayList<>();
  private final TreeExpander myTreeExpander;
  @NotNull private final FileEditor myFileEditor;
  private final StructureView myStructureViewDelegate;
  private boolean myCanClose = true;


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

    myTree = new FileStructureTree(myTreeStructure.getRootElement(), false);

    myTree.setCellRenderer(new NodeRenderer());

    myTree.setTransferHandler(new TransferHandler() {
      @Override
      public boolean importData(@NotNull TransferSupport support) {
        String s = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
        if (s != null && !mySpeedSearch.isPopupActive()) {
          mySpeedSearch.showPopup(s);
          return true;
        }
        return false;
      }

      @Nullable
      @Override
      protected Transferable createTransferable(JComponent component) {
        Set<Object> nodes = myAbstractTreeBuilder.getSelectedElements();
        if (nodes.isEmpty()) return null;
        List<Pair<FilteringTreeStructure.FilteringNode, PsiElement>> result = ContainerUtil.newArrayListWithCapacity(nodes.size());
        for (Object o : nodes) {
          if (!(o instanceof FilteringTreeStructure.FilteringNode)) continue;
          FilteringTreeStructure.FilteringNode node = (FilteringTreeStructure.FilteringNode)o;
          PsiElement psi = getPsi(node);
          ContainerUtil.addIfNotNull(result, Pair.create(node, psi));
        }

        final Set<PsiElement> psiSelection = ContainerUtil.map2LinkedSet(result, Functions.<PsiElement>pairSecond());

        String text = StringUtil.join(result, pair -> {
          PsiElement psi = pair.second;
          String defaultPresentation = pair.first.getPresentation().getPresentableText();
          if (psi == null) return defaultPresentation;
          for (PsiElement p = psi.getParent(); p != null; p = p.getParent()) {
            if (psiSelection.contains(p)) return null;
          }
          return ObjectUtils.chooseNotNull(psi.getText(), defaultPresentation);
        }, "\n");
        
        String htmlText = "<body>\n" + text + "\n</body>";
        return new TextTransferable(XmlStringUtil.wrapInHtml(htmlText), text);
      }

      @Override
      public int getSourceActions(JComponent component) {
        return COPY;
      }
    });

    mySpeedSearch = new MyTreeSpeedSearch();
    mySpeedSearch.setComparator(new SpeedSearchComparator(false, true) {
      @NotNull
      @Override
      protected MinusculeMatcher createMatcher(@NotNull String pattern) {
        return NameUtil.buildMatcher(pattern).withSeparators(" ()").build();
      }
    });

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
    MnemonicHelper.init(panel);
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
      .setCancelCallback(() -> {
        DimensionService.getInstance().setLocation(getDimensionServiceKey(), myPopup.getLocationOnScreen(), myProject);
        return myCanClose;
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
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      ApplicationManager.getApplication().runReadAction(() -> myFilteringStructure.rebuild());

      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> {
        myAbstractTreeBuilder.queueUpdate().doWhenDone(() -> {
          myTreeHasBuilt.setDone();
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(() -> {
            if (myAbstractTreeBuilder.isDisposed()) return;
            if (selectPsiElement(myInitialPsiElement) == null) {
              TreeUtil.ensureSelection(myAbstractTreeBuilder.getTree());
              myAbstractTreeBuilder.revalidateTree();
            }
          });
        });
        installUpdater();
      });
    });
  }

  private void installUpdater() {
    if (ApplicationManager.getApplication().isUnitTestMode() || myPopup.isDisposed()) {
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
          myAbstractTreeBuilder.refilter(null, false, false).doWhenProcessed(() -> {
            //noinspection SSBasedInspection
            SwingUtilities.invokeLater(() -> {
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
            });
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
    Set<PsiElement> parents = new java.util.HashSet<>();

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

  private static boolean isShouldNarrowDown() {
    return PropertiesComponent.getInstance().getBoolean(narrowDownPropertyKey, true);
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
    List<FileStructureFilter> fileStructureFilters = new ArrayList<>();
    List<FileStructureNodeProvider> fileStructureNodeProviders = new ArrayList<>();
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
    new DumbAwareAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        final boolean succeeded = navigateSelectedElement();
        if (succeeded) {
          unregisterCustomShortcutSet(panel);
        }
      }
    }.registerCustomShortcutSet(shortcutSet, panel);

    new DumbAwareAction() {
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
    final JPanel topPanel = new JPanel(new BorderLayout());
    topPanel.add(comboPanel);

    topPanel.add(createSettingsButton(), BorderLayout.EAST);

    myPreferredWidth = Math.max(comboPanel.getPreferredSize().width, JBUI.scale(350));
    panel.add(topPanel, BorderLayout.NORTH);
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myAbstractTreeBuilder.getTree());
    scrollPane.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP | SideBorder.BOTTOM));
    panel.add(scrollPane, BorderLayout.CENTER);
    //panel.add(createSouthPanel(), BorderLayout.SOUTH);
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
          return ContainerUtil.toArray(getPsiElementsFromSelection(), PsiElement.ARRAY_FACTORY);
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

  @NotNull
  private List<PsiElement> getPsiElementsFromSelection() {
    Set<Object> nodes = myAbstractTreeBuilder.getSelectedElements();
    if (nodes.isEmpty()) return Collections.emptyList();
    List<PsiElement> result = ContainerUtil.newArrayListWithCapacity(nodes.size());
    for (Object o : nodes) {
      if (!(o instanceof FilteringTreeStructure.FilteringNode)) continue;
      ContainerUtil.addIfNotNull(result, getPsi((FilteringTreeStructure.FilteringNode)o));
    }
    return result;
  }

  @NotNull
  protected JComponent createSettingsButton() {
    final JLabel label = new JLabel(AllIcons.General.SecondaryGroup);
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        DefaultActionGroup group = new DefaultActionGroup();
        //addSorters(group);
        //addGroupers(group);
        //addFilters(group);

        group.add(new ToggleAction(IdeBundle.message("checkbox.narrow.down.on.typing")) {
          @Override
          public boolean isSelected(AnActionEvent e) {
            return isShouldNarrowDown();
          }

          @Override
          public void setSelected(AnActionEvent e, boolean state) {
            PropertiesComponent.getInstance().setValue(narrowDownPropertyKey, Boolean.toString(state));
            if (mySpeedSearch.isPopupActive() && !StringUtil.isEmpty(mySpeedSearch.getEnteredPrefix())) {
              myAbstractTreeBuilder.queueUpdate();
            }
          }
        });

        final DataManager dataManager = DataManager.getInstance();
        assert dataManager != null;
        final ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(null, group,
                                                                                    dataManager.getDataContext(label),
                                                                                    JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                                                                                    false);
        popup.addListener(
            new JBPopupListener() {
              @Override
              public void beforeShown(LightweightWindowEvent event) {

              }

              @Override
              public void onClosed(LightweightWindowEvent event) {
                myCanClose = true;
              }
            });
        myCanClose = false;
        popup.showUnderneathOf(label);
        return true;
      }
    }.installOn(label);
    return label;
  }

  protected void addFilters(DefaultActionGroup group) {
    Filter[] filters = myTreeModel.getFilters();
    for (Filter filter : filters) {
      group.add(new TreeActionWrapper(filter, this));
    }
  }

  protected void addGroupers(DefaultActionGroup group) {
    Grouper[] groupers = myTreeModel.getGroupers();
    for (Grouper grouper : groupers) {
      group.add(new TreeActionWrapper(grouper, this));
    }
  }

  protected void addSorters(DefaultActionGroup group) {
    Sorter[] sorters = myTreeModel.getSorters();
    for (final Sorter sorter : sorters) {
      if (sorter.isVisible()) {
        group.add(new TreeActionWrapper(sorter, this));
      }
    }
    if (sorters.length > 0) {
      group.addSeparator();
    }
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

    final Ref<Boolean> succeeded = new Ref<>();
    final CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(myProject, () -> {
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
    }, "Navigate", null);
    return succeeded.get();
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
    final boolean isRevertedStructureFilter = action instanceof FileStructureFilter && ((FileStructureFilter)action).isReverted();
    myTreeActionsOwner.setActionIncluded(action, isRevertedStructureFilter ? !selected : selected);
    chkFilter.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        final boolean state = chkFilter.isSelected();
        if (!myAutoClicked.contains(chkFilter)) {
          saveState(action, state);
        }
        myTreeActionsOwner.setActionIncluded(action, isRevertedStructureFilter ? !state : state);
        //final String filter = mySpeedSearch.isPopupActive() ? mySpeedSearch.getEnteredPrefix() : null;
        //mySpeedSearch.hidePopup();
        Object selection = ContainerUtil.getFirstItem(myAbstractTreeBuilder.getSelectedElements());
        if (selection instanceof FilteringTreeStructure.FilteringNode) {
          selection = ((FilteringTreeStructure.FilteringNode)selection).getDelegate();
        }
        myTreeStructure.rebuildTree();
        myFilteringStructure.rebuild();

        final Object sel = selection;
        final Runnable runnable = () -> ApplicationManager.getApplication().runReadAction(() -> {
          myAbstractTreeBuilder.refilter(sel, true, false).doWhenProcessed(() -> {
            if (mySpeedSearch.isPopupActive()) {
              mySpeedSearch.refreshSelection();
            }
          });
        });
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
      new DumbAwareAction() {
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
      return PropertiesComponent.getInstance().getBoolean(TreeStructureUtil.getPropertyName(propertyName));
    }

    return false;
  }

  private static void saveState(TreeAction action, boolean state) {
    if (action instanceof PropertyOwner) {
      final String propertyName = ((PropertyOwner)action).getPropertyName();
      PropertiesComponent.getInstance().setValue(TreeStructureUtil.getPropertyName(propertyName), state);
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
    // NB!: this point is achievable if the following method returns null
    // see com.intellij.ide.util.treeView.NodeDescriptor.toString
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

  @Override
  public void setActionActive(String name, boolean state) {

  }

  @Override
  public boolean isActionActive(String name) {
    return false;
  }

  private class FileStructurePopupFilter implements ElementFilter {
    private String myLastFilter = null;
    private final Set<Object> myVisibleParents = new HashSet<>();
    private final boolean isUnitTest = ApplicationManager.getApplication().isUnitTestMode();

    @Override
    public boolean shouldBeShowing(Object value) {
      if (!isShouldNarrowDown()) return true;

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
      ArrayList<SpeedSearchObjectWithWeight> cur = new ArrayList<>();
      int max = -1;
      for (SpeedSearchObjectWithWeight p : paths) {
        final Object last = ((TreePath)p.node).getLastPathComponent();
        final List<PsiElement> elements = new ArrayList<>();
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

      Collections.sort(cur, (o1, o2) -> {
        final int i = o1.compareWith(o2);
        return i != 0 ? i
                      : ((TreePath)o2.node).getPathCount() - ((TreePath)o1.node).getPathCount();
      });
      return cur.isEmpty() ? null : cur.get(0).node;
    }
  }

  class FileStructureTree extends JBTreeWithHintProvider implements AlwaysExpandedTree, PlaceProvider<String> {
    private final boolean fast;

    public FileStructureTree(Object rootElement, boolean fastExpand) {
      super(new DefaultMutableTreeNode(rootElement));
      if (fastExpand) {
        Hashtable hashtable = new Hashtable() {
          @Override
          public synchronized Object get(Object key) {
            return Boolean.TRUE;
          }
        };
        fast = ReflectionUtil.setField(JTree.class, this, Hashtable.class, "expandedState", hashtable);
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

    @Override
    public String getPlace() {
      return ActionPlaces.STRUCTURE_VIEW_POPUP;
    }
  }
}
