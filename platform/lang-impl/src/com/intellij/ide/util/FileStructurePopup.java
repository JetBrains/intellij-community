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
import com.intellij.ide.*;
import com.intellij.ide.actions.ViewStructureAction;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.ide.structureView.ModelListener;
import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.ide.structureView.newStructureView.TreeActionWrapper;
import com.intellij.ide.structureView.newStructureView.TreeActionsOwner;
import com.intellij.ide.structureView.newStructureView.TreeModelWrapper;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.ide.util.treeView.ValidateableNode;
import com.intellij.ide.util.treeView.smartTree.*;
import com.intellij.navigation.LocationPresentation;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.*;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.HintUpdateSupply;
import com.intellij.ui.popup.PopupUpdateProcessor;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.filtered.FilteringTreeBuilder;
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.TextTransferable;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.util.*;
import java.util.List;

import static com.intellij.ide.structureView.newStructureView.StructureViewComponent.*;
import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;

/**
 * @author Konstantin Bulenkov
 */
public class FileStructurePopup implements Disposable, TreeActionsOwner {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.FileStructurePopup");
  private static final String NARROW_DOWN_PROPERTY_KEY = "FileStructurePopup.narrowDown";

  private final boolean myUseATM = true; //todo inline & remove

  private final Project myProject;
  private final FileEditor myFileEditor;
  private final StructureViewModel myTreeModelWrapper;
  private final StructureViewModel myTreeModel;
  private final TreeStructureActionsOwner myTreeActionsOwner;

  private JBPopup myPopup;
  private String myTitle;

  private final Tree myTree;
  private final SmartTreeStructure myTreeStructure;
  private final FilteringTreeStructure myFilteringStructure;
  private final FilteringTreeBuilder myTreeBuilder;

  private final AsyncTreeModel myAsyncTreeModel;
  private final StructureTreeModel myStructureTreeModel;
  private final TreeSpeedSearch mySpeedSearch;

  private final Object myInitialElement;
  private final Map<Class, JBCheckBox> myCheckBoxes = new HashMap<>();
  private final List<JBCheckBox> myAutoClicked = new ArrayList<>();
  private String myTestSearchFilter;
  private final ActionCallback myTreeHasBuilt = new ActionCallback();
  private boolean myInitialNodeIsLeaf;
  private final List<Pair<String, JBCheckBox>> myTriggeredCheckboxes = new ArrayList<>();
  private final TreeExpander myTreeExpander;
  private final CopyPasteDelegator myCopyPasteDelegator;

  private boolean myCanClose = true;
  private boolean myDisposed;

  /** @noinspection unused*/
  @Deprecated
  public FileStructurePopup(@NotNull Project project,
                            @NotNull FileEditor fileEditor,
                            @NotNull StructureView structureView,
                            boolean applySortAndFilter) {
    this(project, fileEditor, ViewStructureAction.createStructureViewModel(project, fileEditor, structureView));
    Disposer.register(this, structureView);
  }

  public FileStructurePopup(@NotNull Project project,
                            @NotNull FileEditor fileEditor,
                            @NotNull StructureViewModel treeModel) {
    myProject = project;
    myFileEditor = fileEditor;
    myTreeModel = treeModel;

    //Stop code analyzer to speedup EDT
    DaemonCodeAnalyzer.getInstance(myProject).disableUpdateByTimer(this);
    IdeFocusManager.getInstance(myProject).typeAheadUntil(myTreeHasBuilt, "FileStructurePopup");

    myTreeActionsOwner = new TreeStructureActionsOwner(myTreeModel);
    myTreeActionsOwner.setActionIncluded(Sorter.ALPHA_SORTER, true);
    myTreeModelWrapper = new TreeModelWrapper(myTreeModel, myTreeActionsOwner);

    myTreeStructure = new SmartTreeStructure(project, myTreeModelWrapper) {
      @Override
      public void rebuildTree() {
        if (!ApplicationManager.getApplication().isUnitTestMode() && myPopup.isDisposed()) {
          return;
        }
        super.rebuildTree();
        myFilteringStructure.rebuild();
      }

      @Override
      public boolean isToBuildChildrenInBackground(Object element) {
        return getRootElement() == element;
      }

      @Override
      protected TreeElementWrapper createTree() {
        return createWrapper(myProject, myModel.getRoot(), myModel);
      }

      @NonNls
      @Override
      public String toString() {
        return "structure view tree structure(model=" + myTreeModelWrapper + ")";
      }
    };

    FileStructurePopupFilter filter = new FileStructurePopupFilter();
    myFilteringStructure = new FilteringTreeStructure(filter, myTreeStructure, false);

    if (myUseATM) {
      myStructureTreeModel = new StructureTreeModel(true);
      myStructureTreeModel.setStructure(myFilteringStructure);
      myAsyncTreeModel = new AsyncTreeModel(myStructureTreeModel);
      myAsyncTreeModel.setRootImmediately(myStructureTreeModel.getRootImmediately());
      myTree = new MyTree(myAsyncTreeModel);
      registerAutoExpandListener(myTree, myTreeModel);
      Disposer.register(this, () -> myTreeModelWrapper.dispose());
      Disposer.register(this, myAsyncTreeModel);

      myTreeBuilder = null;
    }
    else {
      myStructureTreeModel = null;
      myAsyncTreeModel = null;
      myTree = new MyTree(new DefaultTreeModel(new DefaultMutableTreeNode(myFilteringStructure.getRootElement())));
      myTreeBuilder = new FilteringTreeBuilder(myTree, filter, myFilteringStructure, null) {
        @Override
        public void initRootNode() {
        }

        @Override
        protected boolean validateNode(Object child) {
          Object o = child instanceof FilteringTreeStructure.FilteringNode ?
                     ((FilteringTreeStructure.FilteringNode)child).getDelegate() : child;
          return !(o instanceof ValidateableNode) || ((ValidateableNode)o).isValid();
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
      Disposer.register(this, myTreeBuilder);
      myTreeBuilder.getUi().getUpdater().setDelay(1);
    }
    ModelListener modelListener = new ModelListener() {
      @Override
      public void onModelChanged() {
        rebuild(false);
      }
    };
    myTreeModel.addModelListener(modelListener);
    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        myTreeModel.removeModelListener(modelListener);
      }
    });
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
        JBIterable<Pair<FilteringTreeStructure.FilteringNode, PsiElement>> pairs = JBIterable.of(myTree.getSelectionPaths())
          .filterMap(o -> TreeUtil.getUserObject(o.getLastPathComponent()))
          .filter(FilteringTreeStructure.FilteringNode.class)
          .filterMap(o -> o.getDelegate() instanceof PsiElement ? Pair.create(o, (PsiElement)o.getDelegate()) : null)
          .collect();
        if (pairs.isEmpty()) return null;
        Set<PsiElement> psiSelection = pairs.map(Functions.pairSecond()).toSet();

        String text = StringUtil.join(pairs, pair -> {
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

    myTreeExpander = new DefaultTreeExpander(myTree);
    myCopyPasteDelegator = createCopyPasteDelegator(myProject, myTree);

    myInitialElement = myTreeModel.getCurrentEditorElement();
    TreeUtil.installActions(myTree);
  }

  public void show() {
    //final long time = System.currentTimeMillis();
    JComponent panel = createCenterPanel();
    MnemonicHelper.init(panel);
    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        if (myPopup.isVisible()) {
          PopupUpdateProcessor updateProcessor = myPopup.getUserData(PopupUpdateProcessor.class);
          if (updateProcessor != null) {
            AbstractTreeNode node = getSelectedNode();
            updateProcessor.updatePopup(node);
          }
        }
      }
    });

    myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, myTree)
      .setTitle(myTitle)
      .setResizable(true)
      .setModalContext(false)
      .setFocusable(true)
      .setRequestFocus(true)
      .setMovable(true)
      .setBelongsToGlobalPopupStack(true)
      //.setCancelOnClickOutside(false) //for debug and snapshots
      .setCancelKeyEnabled(false)
      .setDimensionServiceKey(null, getDimensionServiceKey(), true)
      .setCancelCallback(() -> myCanClose)
      .createPopup();

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
    myPopup.showCenteredInCurrentWindow(myProject);

    ((AbstractPopup)myPopup).setShowHints(true);

    IdeFocusManager.getInstance(myProject).requestFocus(myTree, true);
    Window window = SwingUtilities.windowForComponent(myPopup.getContent());
    WindowFocusListener windowFocusListener = new WindowAdapter() {
      @Override
      public void windowLostFocus(WindowEvent e) {
        myPopup.cancel();
      }
    };
    window.addWindowFocusListener(windowFocusListener);
    Disposer.register(myPopup, () -> window.removeWindowFocusListener(windowFocusListener));

    rebuildAndSelect(false, myInitialElement).processed(path -> UIUtil.invokeLaterIfNeeded(() -> {
      TreeUtil.ensureSelection(myTree);
      myTreeHasBuilt.setDone();
      installUpdater();
    }));
  }

  private void installUpdater() {
    if (ApplicationManager.getApplication().isUnitTestMode() || myPopup.isDisposed()) {
      return;
    }
    Alarm alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, myPopup);
    alarm.addRequest(new Runnable() {
      String filter = "";

      @Override
      public void run() {
        alarm.cancelAllRequests();
        String prefix = mySpeedSearch.getEnteredPrefix();
        myTree.getEmptyText().setText(StringUtil.isEmpty(prefix) ? "Structure is empty" : "'" + prefix + "' not found");
        if (prefix == null) prefix = "";

        if (!filter.equals(prefix)) {
          boolean isBackspace = prefix.length() < filter.length();
          filter = prefix;
          rebuild(true).processed(ignore -> UIUtil.invokeLaterIfNeeded(() -> {
            if (isDisposed()) return;
            if (isBackspace && handleBackspace(filter)) {
              return;
            }
            if (myFilteringStructure.getRootElement().getChildren().length == 0) {
              for (JBCheckBox box : myCheckBoxes.values()) {
                if (!box.isSelected()) {
                  myAutoClicked.add(box);
                  myTriggeredCheckboxes.add(0, Pair.create(filter, box));
                  box.doClick();
                  filter = "";
                  break;
                }
              }
            }
          }));
        }
        if (!alarm.isDisposed()) {
          alarm.addRequest(this, 300);
        }
      }
    }, 300);
  }

  private boolean handleBackspace(String filter) {
    boolean clicked = false;
    Iterator<Pair<String, JBCheckBox>> iterator = myTriggeredCheckboxes.iterator();
    while (iterator.hasNext()) {
      Pair<String, JBCheckBox> next = iterator.next();
      if (next.getFirst().length() < filter.length()) break;

      if (next.getFirst().length() >= filter.length()) {
        iterator.remove();
        next.getSecond().doClick();
        clicked = true;
      }
    }
    return clicked;
  }

  @NotNull
  public Promise<TreePath> select(Object element) {
    if (!myUseATM) {
      FilteringTreeStructure.FilteringNode node =
        element instanceof PsiElement ? selectPsiElement((PsiElement)element) : null;
      if (node == null) return Promises.rejectedPromise();
      return Promise.resolve(new TreePath(node));
    }
    AsyncPromise<TreePath> result = new AsyncPromise<>();
    int[] stage = {1, 0}; // 1 - first pass, 2 - optimization applied, 3 - retry w/o optimization
    TreePath[] deepestPath = {null};
    TreeVisitor visitor = path -> {
      Object last = path.getLastPathComponent();
      Object userObject = unwrapNavigatable(last);
      Object value = unwrapValue(last);
      if (Comparing.equal(value, element) ||
          userObject instanceof AbstractTreeNode && ((AbstractTreeNode)userObject).canRepresent(element)) {
        return TreeVisitor.Action.INTERRUPT;
      }
      if (value instanceof PsiElement && element instanceof PsiElement) {
        if (PsiTreeUtil.isAncestor((PsiElement)value, (PsiElement)element, true)) {
          int count = path.getPathCount();
          if (stage[1] == 0 || stage[1] < count) {
            stage[1] = count;
            deepestPath[0] = path;
          }
        }
        else if (stage[0] != 3) {
          stage[0] = 2;
          return TreeVisitor.Action.SKIP_CHILDREN;
        }
      }
      return TreeVisitor.Action.CONTINUE;
    };
    Function<TreePath, Promise<TreePath>> action = path -> {
      myTree.expandPath(path);
      TreeUtil.selectPath(myTree, path);
      TreeUtil.ensureSelection(myTree);
      Object userObject = path == null ? null : TreeUtil.getUserObject(path.getLastPathComponent());
      if (userObject != null && Comparing.equal(element, unwrapValue(userObject))) {
        myInitialNodeIsLeaf = myFilteringStructure.getChildElements(userObject).length == 0;
      }
      return Promises.resolvedPromise(path);
    };
    Function<TreePath, Promise<TreePath>> fallback = new Function<TreePath, Promise<TreePath>>() {
      @Override
      public Promise<TreePath> fun(TreePath path) {
        if (path == null && stage[0] == 2) {
          // Some structure views merge unrelated psi elements into a structure node (MarkdownStructureViewModel).
          // So turn off the isAncestor() optimization and retry once.
          stage[0] = 3;
          return myAsyncTreeModel.accept(visitor).thenAsync(this);
        }
        else {
          TreePath adjusted = path == null ? deepestPath[0] : path;
          return adjusted == null ? Promises.rejectedPromise() : action.fun(adjusted);
        }
      }
    };
    myAsyncTreeModel.accept(visitor).thenAsync(fallback).processed(result);
    return result;
  }

  @TestOnly
  public AsyncPromise<Void> rebuildAndUpdate() {
    AsyncPromise<Void> result = new AsyncPromise<>();
    if (!myUseATM) {
      rebuild(false).notify(result);
      return result;
    }
    TreeVisitor visitor = path -> {
      Object o = TreeUtil.getUserObject(path.getLastPathComponent());
      if (o instanceof AbstractTreeNode) ((AbstractTreeNode)o).update();
      return TreeVisitor.Action.CONTINUE;
    };
    rebuild(false).processed(ignore1 -> myAsyncTreeModel.accept(visitor).processed(ignore2 -> result.setResult(null)));
    return result;
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  @Override
  public void dispose() {
    myDisposed = true;
  }

  private static boolean isShouldNarrowDown() {
    return PropertiesComponent.getInstance().getBoolean(NARROW_DOWN_PROPERTY_KEY, true);
  }

  @NonNls
  protected static String getDimensionServiceKey() {
    return "StructurePopup";
  }

  @Nullable
  public PsiElement getCurrentElement(@Nullable final PsiFile psiFile) {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    Object elementAtCursor = myTreeModelWrapper.getCurrentEditorElement();
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
      for (Filter filter : myTreeModel.getFilters()) {
        if (filter instanceof FileStructureFilter) {
          FileStructureFilter fsFilter = (FileStructureFilter)filter;
          myTreeActionsOwner.setActionIncluded(fsFilter, true);
          fileStructureFilters.add(fsFilter);
        }
      }

      if (myTreeModel instanceof ProvidingTreeModel) {
        for (NodeProvider provider : ((ProvidingTreeModel)myTreeModel).getNodeProviders()) {
          if (provider instanceof FileStructureNodeProvider) {
            fileStructureNodeProviders.add((FileStructureNodeProvider)provider);
          }
        }
      }
    }
    int checkBoxCount = fileStructureNodeProviders.size() + fileStructureFilters.size();
    JPanel panel = new JPanel(new BorderLayout());
    JPanel chkPanel = new JPanel(new GridLayout(0, checkBoxCount > 0 && checkBoxCount % 4 == 0 ? checkBoxCount / 2 : 3, 0, 0));

    Shortcut[] F4 = ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE).getShortcutSet().getShortcuts();
    Shortcut[] ENTER = CustomShortcutSet.fromString("ENTER").getShortcuts();
    CustomShortcutSet shortcutSet = new CustomShortcutSet(ArrayUtil.mergeArrays(F4, ENTER));
    new DumbAwareAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        boolean succeeded = navigateSelectedElement();
        if (succeeded) {
          unregisterCustomShortcutSet(panel);
        }
      }
    }.registerCustomShortcutSet(shortcutSet, panel);

    DumbAwareAction.create(e -> {
      if (mySpeedSearch != null && mySpeedSearch.isPopupActive()) {
        mySpeedSearch.hidePopup();
      }
      else {
        myPopup.cancel();
      }
    }).registerCustomShortcutSet(CustomShortcutSet.fromString("ESCAPE"), myTree);
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        TreePath path = myTree.getClosestPathForLocation(e.getX(), e.getY());
        Rectangle bounds = path == null ? null : myTree.getPathBounds(path);
        if (bounds == null || 
            bounds.x > e.getX() ||
            bounds.y > e.getY() || bounds.y + bounds.height < e.getY()) return false;
        navigateSelectedElement();
        return true;
      }
    }.installOn(myTree);

    for (FileStructureFilter filter : fileStructureFilters) {
      addCheckbox(chkPanel, filter);
    }

    for (FileStructureNodeProvider provider : fileStructureNodeProviders) {
      addCheckbox(chkPanel, provider);
    }
    JPanel topPanel = new JPanel(new BorderLayout());
    topPanel.add(chkPanel, BorderLayout.WEST);

    topPanel.add(createSettingsButton(), BorderLayout.EAST);

    panel.add(topPanel, BorderLayout.NORTH);
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTree);
    scrollPane.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP | SideBorder.BOTTOM));
    Dimension preferredSize = scrollPane.getPreferredSize();
    preferredSize.width = Math.max(chkPanel.getPreferredSize().width, JBUI.scale(350));
    scrollPane.setPreferredSize(preferredSize);
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
        if (OpenFileDescriptor.NAVIGATE_IN_EDITOR.is(dataId)) {
          if (myFileEditor instanceof TextEditor) {
            return ((TextEditor)myFileEditor).getEditor();
          }
        }
        if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
          return getSelectedElements().filter(PsiElement.class).first();
        }
        if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
          return PsiUtilCore.toPsiElementArray(getSelectedElements().filter(PsiElement.class).toList());
        }
        if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
          return getSelectedElements().filter(Navigatable.class).first();
        }
        if (CommonDataKeys.NAVIGATABLE_ARRAY.is(dataId)) {
          List<Navigatable> result = getSelectedElements().filter(Navigatable.class).toList();
          return result.isEmpty() ? null : result.toArray(new Navigatable[result.size()]);
        }
        if (LangDataKeys.POSITION_ADJUSTER_POPUP.is(dataId)) {
          return myPopup;
        }
        if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
          return myCopyPasteDelegator.getCopyProvider();
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
  private JBIterable<Object> getSelectedElements() {
    return JBIterable.of(myTree.getSelectionPaths())
      .filterMap(o -> unwrapValue(o.getLastPathComponent()));
  }

  @NotNull
  protected JComponent createSettingsButton() {
    JLabel label = new JLabel(AllIcons.General.SecondaryGroup);
    label.setBorder(JBUI.Borders.empty(0, 2));
    label.setHorizontalAlignment(SwingConstants.RIGHT);
    label.setVerticalAlignment(SwingConstants.CENTER);

    List<AnAction> sorters = createSorters();
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        DefaultActionGroup group = new DefaultActionGroup();
        if (!sorters.isEmpty()) {
          group.addAll(sorters);
          group.addSeparator();
        }
        //addGroupers(group);
        //addFilters(group);

        group.add(new ToggleAction(IdeBundle.message("checkbox.narrow.down.on.typing")) {
          @Override
          public boolean isSelected(AnActionEvent e) {
            return isShouldNarrowDown();
          }

          @Override
          public void setSelected(AnActionEvent e, boolean state) {
            PropertiesComponent.getInstance().setValue(NARROW_DOWN_PROPERTY_KEY, Boolean.toString(state));
            if (mySpeedSearch.isPopupActive() && !StringUtil.isEmpty(mySpeedSearch.getEnteredPrefix())) {
              rebuild(true);
            }
          }
        });

        DataManager dataManager = DataManager.getInstance();
        ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
          null, group, dataManager.getDataContext(label), JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false);
        popup.addListener(new JBPopupListener.Adapter() {
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

  protected List<AnAction> createSorters() {
    List<AnAction> actions = new ArrayList<>();
    for (Sorter sorter : myTreeModel.getSorters()) {
      if (sorter.isVisible()) {
        actions.add(new MyTreeActionWrapper(sorter));
      }
    }
    return actions;
  }

  private class MyTreeActionWrapper extends TreeActionWrapper {
    private final TreeAction myAction;

    public MyTreeActionWrapper(TreeAction action) {
      super(action, myTreeActionsOwner);
      myAction = action;
      myTreeActionsOwner.setActionIncluded(action, getDefaultValue(action));
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setIcon(null);
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      boolean actionState = TreeModelWrapper.shouldRevert(myAction) ? !state : state;
      myTreeActionsOwner.setActionIncluded(myAction, actionState);
      saveState(myAction, state);
      rebuild(false).processed(ignore -> {
        if (mySpeedSearch.isPopupActive()) {
          mySpeedSearch.refreshSelection();
        }
      });
    }
  }

  @Nullable
  private AbstractTreeNode getSelectedNode() {
    TreePath path = myTree.getSelectionPath();
    Object o = unwrapNavigatable(path == null ? null : path.getLastPathComponent());
    return o instanceof AbstractTreeNode ? (AbstractTreeNode)o : null;
  }

  public boolean navigateSelectedElement() {
    AbstractTreeNode selectedNode = getSelectedNode();
    if (ApplicationManager.getApplication().isInternal()) {
      String enteredPrefix = mySpeedSearch.getEnteredPrefix();
      String itemText = getSpeedSearchText(selectedNode);
      if (StringUtil.isNotEmpty(enteredPrefix) && StringUtil.isNotEmpty(itemText)) {
        LOG.info("Chosen in file structure popup by prefix '" + enteredPrefix + "': '" + itemText + "'");
      }
    }

    Ref<Boolean> succeeded = new Ref<>();
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(myProject, () -> {
      if (selectedNode != null) {
        if (selectedNode.canNavigateToSource()) {
          selectedNode.navigate(true);
          myPopup.cancel();
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

  private void addCheckbox(JPanel panel, TreeAction action) {
    String text = action instanceof FileStructureFilter ? ((FileStructureFilter)action).getCheckBoxText() :
                  action instanceof FileStructureNodeProvider ? ((FileStructureNodeProvider)action).getCheckBoxText() : null;

    if (text == null) return;

    Shortcut[] shortcuts = extractShortcutFor(action);


    JBCheckBox checkBox = new JBCheckBox();
    UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, checkBox);

    boolean selected = getDefaultValue(action);
    checkBox.setSelected(selected);
    boolean isRevertedStructureFilter = action instanceof FileStructureFilter && ((FileStructureFilter)action).isReverted();
    myTreeActionsOwner.setActionIncluded(action, isRevertedStructureFilter != selected);
    checkBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        boolean state = checkBox.isSelected();
        if (!myAutoClicked.contains(checkBox)) {
          saveState(action, state);
        }
        myTreeActionsOwner.setActionIncluded(action, isRevertedStructureFilter != state);
        rebuild(false).processed(ignore -> {
          if (mySpeedSearch.isPopupActive()) {
            mySpeedSearch.refreshSelection();
          }
        });
      }
    });
    checkBox.setFocusable(false);

    if (shortcuts.length > 0) {
      text += " (" + KeymapUtil.getShortcutText(shortcuts[0]) + ")";
      DumbAwareAction.create(e -> checkBox.doClick())
        .registerCustomShortcutSet(new CustomShortcutSet(shortcuts), myTree);
    }
    checkBox.setText(StringUtil.capitalize(StringUtil.trimStart(text.trim(), "Show ")));
    panel.add(checkBox);
    myCheckBoxes.put(action.getClass(), checkBox);
  }

  @NotNull
  private Promise<Void> rebuild(boolean refilterOnly) {
    Object selection = JBIterable.of(myTree.getSelectionPaths())
      .filterMap(o -> unwrapValue(o.getLastPathComponent())).first();
    return rebuildAndSelect(refilterOnly, selection).then(o -> null);
  }

  @NotNull
  private Promise<TreePath> rebuildAndSelect(boolean refilterOnly, Object selection) {
    AsyncPromise<TreePath> result = new AsyncPromise<>();
    if (!myUseATM) {
      if (!refilterOnly) {
        myTreeStructure.rebuildTree();
      }
      myTreeBuilder.refilter(selection, true, false).doWhenProcessed(() -> {
        if (selection instanceof PsiElement) {
          selectPsiElement((PsiElement)selection);
        }
        result.setResult(null);
      });
      return result;
    }
    myStructureTreeModel.getInvoker().invokeLaterIfNeeded(() -> {
      if (refilterOnly) {
        myFilteringStructure.refilter();
        myStructureTreeModel.invalidate(
          () ->
            (selection == null ? myAsyncTreeModel.accept(o -> TreeVisitor.Action.CONTINUE) : select(selection))
              .rejected(ignore2 -> result.setError("rejected"))
              .done(p -> UIUtil.invokeLaterIfNeeded(
                () -> {
                  TreeUtil.expand(getTree(), myTreeModel instanceof StructureViewCompositeModel ? 3 : 2);
                  TreeUtil.ensureSelection(myTree);
                  mySpeedSearch.refreshSelection();
                  result.setResult(p);
                })));
      }
      else {
        myTreeStructure.rebuildTree();
        myStructureTreeModel.invalidate(() -> rebuildAndSelect(true, selection).notify(result));
      }
    });
    return result;
  }

  @NotNull
  static Shortcut[] extractShortcutFor(@NotNull TreeAction action) {
    if (action instanceof ActionShortcutProvider) {
      String actionId = ((ActionShortcutProvider)action).getActionIdForShortcut();
      return getActiveKeymapShortcuts(actionId).getShortcuts();
    }
    return action instanceof FileStructureFilter ?
                           ((FileStructureFilter)action).getShortcut() : ((FileStructureNodeProvider)action).getShortcut();
  }

  private static boolean getDefaultValue(TreeAction action) {
    String propertyName = action instanceof PropertyOwner ? ((PropertyOwner)action).getPropertyName() : action.getName();
    return PropertiesComponent.getInstance().getBoolean(TreeStructureUtil.getPropertyName(propertyName), Sorter.ALPHA_SORTER.equals(action));
  }

  private static void saveState(TreeAction action, boolean state) {
    String propertyName = action instanceof PropertyOwner ? ((PropertyOwner)action).getPropertyName() : action.getName();
    PropertiesComponent.getInstance().setValue(TreeStructureUtil.getPropertyName(propertyName), state);
  }

  public void setTitle(String title) {
    myTitle = title;
  }

  @NotNull
  public Tree getTree() {
    return myTree;
  }

  @TestOnly
  public TreeSpeedSearch getSpeedSearch() {
    return mySpeedSearch;
  }

  @TestOnly
  public void setSearchFilterForTests(String filter) {
    myTestSearchFilter = filter;
  }

  public void setTreeActionState(Class<? extends TreeAction> action, boolean state) {
    JBCheckBox checkBox = myCheckBoxes.get(action);
    if (checkBox != null) {
      checkBox.setSelected(state);
      for (ActionListener listener : checkBox.getActionListeners()) {
        listener.actionPerformed(new ActionEvent(this, 1, ""));
      }
    }
  }

  @Nullable
  public static String getSpeedSearchText(Object object) {
    String text = String.valueOf(object);
    Object value = unwrapWrapper(object);
    if (text != null) {
      if (value instanceof PsiTreeElementBase && ((PsiTreeElementBase)value).isSearchInLocationString()) {
           String locationString = ((PsiTreeElementBase)value).getLocationString();
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
      return text;
    }
    // NB!: this point is achievable if the following method returns null
    // see com.intellij.ide.util.treeView.NodeDescriptor.toString
    if (value instanceof TreeElement) {
      return ReadAction.compute(() -> ((TreeElement)value).getPresentation().getPresentableText());
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

        String text = getSpeedSearchText(value);
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
        SpeedSearchComparator comparator = mySpeedSearch.getComparator();
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

  private class MyTreeSpeedSearch extends TreeSpeedSearch {

    MyTreeSpeedSearch() {
      super(myTree, path -> getSpeedSearchText(TreeUtil.getUserObject(path.getLastPathComponent())), true);
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
      List<SpeedSearchObjectWithWeight> elements = SpeedSearchObjectWithWeight.findElement(s, this);
      return elements.isEmpty() ? null : findClosestTo(myInitialElement, elements);
    }

  }

  static class MyTree extends DnDAwareTree implements PlaceProvider<String> {

    MyTree(TreeModel treeModel) {
      super(treeModel);
      setRootVisible(false);
      setShowsRootHandles(true);
      setHorizontalAutoScrollingEnabled(false);

      HintUpdateSupply.installHintUpdateSupply(this, o -> {
        Object value = unwrapValue(o);
        return value instanceof PsiElement ? (PsiElement)value : null;
      });
    }

    @Override
    public String getPlace() {
      return ActionPlaces.STRUCTURE_VIEW_POPUP;
    }
  }


  // todo remove ASAP ------------------------------------

  @Nullable
  private Object findClosestTo(Object path, List<SpeedSearchObjectWithWeight> paths) {
    if (path == null || !(myInitialElement instanceof PsiElement)) {
      return paths.get(0).node;
    }
    Set<PsiElement> parents = getAllParents((PsiElement)myInitialElement);
    ArrayList<SpeedSearchObjectWithWeight> cur = new ArrayList<>();
    int max = -1;
    for (SpeedSearchObjectWithWeight p : paths) {
      Object object = TreeUtil.getUserObject(((TreePath)p.node).getLastPathComponent());
      if (object instanceof FilteringTreeStructure.FilteringNode) {
        List<PsiElement> elements = new ArrayList<>();
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

  @Nullable
  private static PsiElement getPsi(FilteringTreeStructure.FilteringNode n) {
    return ObjectUtils.tryCast(unwrapValue(n), PsiElement.class);
  }

  @Nullable
  public FilteringTreeStructure.FilteringNode selectPsiElement(PsiElement element) {
    Set<PsiElement> parents = getAllParents(element);

    FilteringTreeStructure.FilteringNode node = (FilteringTreeStructure.FilteringNode)myTreeBuilder.getRootElement();
    if (element != null && node != null && myTreeModel instanceof StructureViewCompositeModel) {
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
        myTreeBuilder.select(node);
        if (myTreeBuilder.getSelectedElements().isEmpty()) {
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
}
