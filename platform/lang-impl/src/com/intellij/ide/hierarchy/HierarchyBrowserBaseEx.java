// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.hierarchy;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.ide.OccurenceNavigatorSupport;
import com.intellij.ide.PsiCopyPasteManager;
import com.intellij.ide.dnd.*;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.ide.hierarchy.actions.BrowseHierarchyActionBase;
import com.intellij.ide.projectView.impl.ProjectViewTree;
import com.intellij.ide.util.scopeChooser.EditScopesDialog;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.TreeBuilderUtil;
import com.intellij.idea.ActionsBundle;
import com.intellij.lang.LangBundle;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.PlatformEditorBundle;
import com.intellij.openapi.fileEditor.PsiElementNavigatable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.search.scope.ProjectProductionScope;
import com.intellij.psi.search.scope.TestsScope;
import com.intellij.psi.search.scope.packageSet.CustomScopesProviderEx;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.popup.HintUpdateSupply;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.usageView.UsageViewTypeLocation;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.SingleAlarm;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.io.File;
import java.text.MessageFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public abstract class HierarchyBrowserBaseEx extends HierarchyBrowserBase implements OccurenceNavigator {
  private static final Logger LOG = Logger.getInstance(HierarchyBrowserBaseEx.class);

  public static final DataKey<HierarchyBrowserBaseEx> HIERARCHY_BROWSER = DataKey.create("HIERARCHY_BROWSER");

  public static final String SCOPE_PROJECT = "Production";
  public static final String SCOPE_ALL = "All";
  public static final String SCOPE_CLASS = "This Class";
  public static final String SCOPE_MODULE = "This Module";
  public static final String SCOPE_TEST = "Test";

  public static final String HELP_ID = "reference.toolWindows.hierarchy";

  private final AtomicReference<Sheet> myCurrentSheet = new AtomicReference<>();

  private final Map<String, Supplier<@Nls String>> myI18nMap;

  private static final class Sheet implements Disposable {
    private AsyncTreeModel myAsyncTreeModel;
    private StructureTreeModel<HierarchyTreeStructure> myStructureTreeModel;
    private final @Nls @NotNull String myType;
    private final JTree myTree;
    private String myScope;
    private final OccurenceNavigator myOccurenceNavigator;

    Sheet(@Nls @NotNull String type, @NotNull JTree tree, @NotNull String scope, @NotNull OccurenceNavigator occurenceNavigator) {
      myType = type;
      myTree = tree;
      myScope = scope;
      myOccurenceNavigator = occurenceNavigator;
    }

    @Override
    public void dispose() {
      myAsyncTreeModel = null;
      myStructureTreeModel = null;
    }
  }

  private final Map<String, Sheet> myType2Sheet = new HashMap<>();
  private final RefreshAction myRefreshAction = new RefreshAction();
  private final SingleAlarm myCursorAlarm = new SingleAlarm(() -> setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)), 100, this);
  private SmartPsiElementPointer<?> mySmartPsiElementPointer;
  private final CardLayout myCardLayout;
  private final JPanel myTreePanel;
  private boolean myCachedIsValidBase;

  public HierarchyBrowserBaseEx(@NotNull Project project, @NotNull PsiElement element) {
    super(project);

    setHierarchyBase(element);

    myCardLayout = new CardLayout();
    myTreePanel = new JPanel(myCardLayout);

    Map<@Nls String, JTree> type2treeMap = new HashMap<>();
    createTrees(type2treeMap);

    myI18nMap = getPresentableNameMap();

    HierarchyBrowserManager.State state = HierarchyBrowserManager.getSettings(project);
    String scope = state.SCOPE == null ? SCOPE_ALL : state.SCOPE;

    for (Map.Entry<@Nls String, JTree> entry : type2treeMap.entrySet()) {
      @Nls String type = entry.getKey();
      JTree tree = entry.getValue();

      OccurenceNavigatorSupport occurenceNavigatorSupport = new OccurenceNavigatorSupport(tree) {
        @Override
        protected @Nullable Navigatable createDescriptorForNode(@NotNull DefaultMutableTreeNode node) {
          HierarchyNodeDescriptor descriptor = getDescriptor(node);
          if (descriptor != null) {
            PsiElement psiElement = getOpenFileElementFromDescriptor(descriptor);
            if (psiElement != null && psiElement.isValid()) {
              return new PsiElementNavigatable(psiElement);
            }
          }
          return null;
        }

        @Override
        public @NotNull String getNextOccurenceActionName() {
          return getNextOccurenceActionNameImpl();
        }

        @Override
        public @NotNull String getPreviousOccurenceActionName() {
          return getPrevOccurenceActionNameImpl();
        }
      };

      myType2Sheet.put(type, new Sheet(type, tree, scope, occurenceNavigatorSupport));
      myTreePanel.add(ScrollPaneFactory.createScrollPane(tree), type);
    }

    JPanel legendPanel = createLegendPanel();
    JPanel contentPanel;
    if (legendPanel == null) {
      contentPanel = myTreePanel;
    }
    else {
      contentPanel = new JPanel(new BorderLayout());
      contentPanel.add(myTreePanel, BorderLayout.CENTER);
      contentPanel.add(legendPanel, BorderLayout.SOUTH);
    }

    buildUi(createToolbar(getActionPlace(), HELP_ID).getComponent(), contentPanel);
  }

  protected @Nullable PsiElement getOpenFileElementFromDescriptor(@NotNull HierarchyNodeDescriptor descriptor) {
    return getElementFromDescriptor(descriptor);
  }

  @Override
  protected abstract @Nullable PsiElement getElementFromDescriptor(@NotNull HierarchyNodeDescriptor descriptor);

  protected abstract @ActionText @NotNull String getPrevOccurenceActionNameImpl();

  protected abstract @ActionText @NotNull String getNextOccurenceActionNameImpl();

  protected abstract void createTrees(@NotNull Map<? super @Nls String, ? super JTree> trees);

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return getOccurrenceNavigator().getActionUpdateThread();
  }

  /**
   * Put (scope type -> presentable name) pairs into a map.
   * This map is used in {@link #changeView(String, boolean)} method to get a proper localization in UI.
   */
  protected @NotNull Map<String, Supplier<@Nls String>> getPresentableNameMap() {
    Map<String, Supplier<String>> map = new HashMap<>();
    map.put(SCOPE_PROJECT, () -> ProjectProductionScope.INSTANCE.getPresentableName());
    map.put(SCOPE_CLASS, () -> LangBundle.message("this.class.scope.name"));
    map.put(SCOPE_MODULE, () -> LangBundle.message("this.module.scope.name"));
    map.put(SCOPE_TEST, () -> TestsScope.INSTANCE.getPresentableName());
    map.put(SCOPE_ALL, () -> CustomScopesProviderEx.getAllScope().getPresentableName());
    return map;
  }


  protected abstract @Nullable JPanel createLegendPanel();

  protected abstract boolean isApplicableElement(@NotNull PsiElement element);

  protected boolean isApplicableElementForBaseOn(@NotNull PsiElement element) {
    return isApplicableElement(element);
  }

  protected abstract @Nullable HierarchyTreeStructure createHierarchyTreeStructure(@NotNull String type, @NotNull PsiElement psiElement);

  protected abstract @Nullable Comparator<NodeDescriptor<?>> getComparator();

  protected abstract @NotNull String getActionPlace();

  protected @Nullable Color getFileColorForNode(Object node) {
    if (node instanceof HierarchyNodeDescriptor) {
      return ((HierarchyNodeDescriptor)node).getBackgroundColorCached();
    }
    return null;
  }

  protected final @NotNull JTree createTree(boolean dndAware) {
    Tree tree;

    if (dndAware) {
      tree = new DnDAwareTree() {
        @Override
        public void addNotify() {
          super.addNotify();
          myRefreshAction.registerShortcutOn(this);
        }

        @Override
        public void removeNotify() {
          super.removeNotify();
          myRefreshAction.unregisterCustomShortcutSet(this);
        }

        @Override
        public boolean isFileColorsEnabled() {
          return ProjectViewTree.isFileColorsEnabledFor(this);
        }

        @Override
        public Color getFileColorFor(Object object) {
          return getFileColorForNode(object);
        }
      };

      if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
        DnDManager.getInstance().registerSource(new DnDSource() {
          @Override
          public boolean canStartDragging(DnDAction action, @NotNull Point dragOrigin) {
            return getSelectedElements().length > 0;
          }

          @Override
          public DnDDragStartBean startDragging(DnDAction action, @NotNull Point dragOrigin) {
            return new DnDDragStartBean(new TransferableWrapper() {
              @Override
              public TreeNode[] getTreeNodes() {
                return tree.getSelectedNodes(TreeNode.class, null);
              }

              @Override
              public PsiElement[] getPsiElements() {
                return getSelectedElements();
              }

              @Override
              public List<File> asFileList() {
                return PsiCopyPasteManager.asFileList(getPsiElements());
              }
            });
          }
        }, tree);
      }
    }
    else {
      tree = new Tree()  {
        @Override
        public void addNotify() {
          super.addNotify();
          myRefreshAction.registerShortcutOn(this);
        }

        @Override
        public void removeNotify() {
          super.removeNotify();
          myRefreshAction.unregisterCustomShortcutSet(this);
        }

        @Override
        public boolean isFileColorsEnabled() {
          return ProjectViewTree.isFileColorsEnabledFor(this);
        }

        @Override
        public Color getFileColorFor(Object object) {
          return getFileColorForNode(object);
        }
      };
    }
    HintUpdateSupply.installDataContextHintUpdateSupply(tree);
    configureTree(tree);
    EditSourceOnDoubleClickHandler.install(tree);
    EditSourceOnEnterKeyHandler.install(tree);
    return tree;
  }

  protected void setHierarchyBase(@NotNull PsiElement element) {
    mySmartPsiElementPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(element);
  }

  protected PsiElement getHierarchyBase() {
    return mySmartPsiElementPointer.getElement();
  }

  private void restoreCursor() {
    myCursorAlarm.cancelAllRequests();
    setCursor(Cursor.getDefaultCursor());
  }

  private void setWaitCursor() {
    myCursorAlarm.request();
  }

  public void changeView(@Nls @NotNull String typeName) {
    changeView(typeName, true);
  }

  public void changeView(@Nls @NotNull String typeName, boolean requestFocus) {
    ThreadingAssertions.assertEventDispatchThread();
    Sheet sheet = myType2Sheet.get(typeName);
    myCurrentSheet.set(sheet);

    PsiElement element = mySmartPsiElementPointer.getElement();
    if (element == null || !isApplicableElement(element)) {
      return;
    }

    if (myContent != null) {
      Supplier<@Nls String> supplier = myI18nMap.computeIfAbsent(typeName, __ -> () -> typeName);
      String displayName = getContentDisplayName(supplier.get(), element);
      if (displayName != null) {
        myContent.setDisplayName(displayName);
      }
    }

    myCardLayout.show(myTreePanel, typeName);

    if (sheet.myStructureTreeModel == null) {
      try {
        setWaitCursor();
        JTree tree = sheet.myTree;

        HierarchyTreeStructure structure = createHierarchyTreeStructure(typeName, element);
        if (structure == null) {
          return;
        }
        StructureTreeModel<HierarchyTreeStructure> myModel = new StructureTreeModel<>(structure, getComparator(), sheet);
        AsyncTreeModel atm = new AsyncTreeModel(myModel, sheet);
        tree.setModel(atm);

        sheet.myStructureTreeModel = myModel;
        sheet.myAsyncTreeModel = atm;
        selectLater(tree, structure.getBaseDescriptor());
        expandLater(tree, structure.getBaseDescriptor());
      }
      finally {
        restoreCursor();
      }
    }

    if (requestFocus) {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(getCurrentTree(), true));
    }
  }

  private static boolean isAncestor(@NotNull Project project,
                                    @NotNull HierarchyNodeDescriptor ancestor,
                                    @NotNull HierarchyNodeDescriptor child) {
    PsiElement ancestorElement = ancestor.getPsiElement();
    while (child != null) {
      PsiElement childElement = child.getPsiElement();
      if (PsiManager.getInstance(project).areElementsEquivalent(ancestorElement, childElement)) return true;
      child = (HierarchyNodeDescriptor)child.getParentDescriptor();
    }
    return false;
  }

  private void selectLater(@NotNull JTree tree, @NotNull HierarchyNodeDescriptor descriptor) {
    TreeUtil.promiseSelect(tree, visitor(descriptor));
  }
  private void selectLater(@NotNull JTree tree, @NotNull List<? extends HierarchyNodeDescriptor> descriptors) {
    TreeUtil.promiseSelect(tree, descriptors.stream().map(descriptor -> visitor(descriptor)));
  }
  private void expandLater(@NotNull JTree tree, @NotNull HierarchyNodeDescriptor descriptor) {
    TreeUtil.promiseExpand(tree, visitor(descriptor));
  }

  private @NotNull TreeVisitor visitor(@NotNull HierarchyNodeDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    if (element == null) return path -> TreeVisitor.Action.INTERRUPT;
    PsiManager psiManager = element.getManager();
    return path -> {
      Object component = path.getLastPathComponent();
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)component;
      Object object = node.getUserObject();
      HierarchyNodeDescriptor current = (HierarchyNodeDescriptor)object;
      PsiElement currentPsiElement = current.getPsiElement();
      if (psiManager.areElementsEquivalent(currentPsiElement, element)) return TreeVisitor.Action.INTERRUPT;
      return isAncestor(myProject, current, descriptor) ? TreeVisitor.Action.CONTINUE : TreeVisitor.Action.SKIP_CHILDREN;
    };
  }

  protected @Nullable @NlsContexts.TabTitle String getContentDisplayName(@Nls @NotNull String typeName, @NotNull PsiElement element) {
    if (element instanceof PsiNamedElement) {
      return MessageFormat.format(typeName, ((PsiNamedElement)element).getName());
    }
    return null;
  }

  @Override
  protected void appendActions(@NotNull DefaultActionGroup actionGroup, @Nullable String helpID) {
    prependActions(actionGroup);
    actionGroup.add(myRefreshAction);
    super.appendActions(actionGroup, helpID);
  }

  protected void prependActions(@NotNull DefaultActionGroup actionGroup) {
  }

  @Override
  public boolean hasNextOccurence() {
    return getOccurrenceNavigator().hasNextOccurence();
  }

  private @NotNull OccurenceNavigator getOccurrenceNavigator() {
    Sheet sheet = myCurrentSheet.get();
    OccurenceNavigator navigator = sheet == null ? null : sheet.myOccurenceNavigator;
    return navigator == null ? EMPTY : navigator;
  }

  @Override
  public boolean hasPreviousOccurence() {
    return getOccurrenceNavigator().hasPreviousOccurence();
  }

  @Override
  public OccurenceInfo goNextOccurence() {
    return getOccurrenceNavigator().goNextOccurence();
  }

  @Override
  public OccurenceInfo goPreviousOccurence() {
    return getOccurrenceNavigator().goPreviousOccurence();
  }

  @Override
  public @NotNull String getNextOccurenceActionName() {
    return getOccurrenceNavigator().getNextOccurenceActionName();
  }

  @Override
  public @NotNull String getPreviousOccurenceActionName() {
    return getOccurrenceNavigator().getPreviousOccurenceActionName();
  }

  public @NotNull StructureTreeModel<?> getTreeModel(@NotNull String viewType) {
    ThreadingAssertions.assertEventDispatchThread();
    return myType2Sheet.get(viewType).myStructureTreeModel;
  }

  @Override
  public void setContent(@NotNull Content content) {
    super.setContent(content);
    // stop all background tasks when toolwindow closed
    Disposer.register(content, this::disposeAllSheets);
  }

  @Override
  StructureTreeModel<?> getCurrentBuilder() {
    String viewType = getCurrentViewType();
    if (viewType == null) {
      return null;
    }
    Sheet sheet = myType2Sheet.get(viewType);
    return sheet == null ? null : sheet.myStructureTreeModel;
  }

  final boolean isValidBase() {
    if (myProject.isDisposed()) return false;
    if (PsiDocumentManager.getInstance(myProject).getUncommittedDocuments().length > 0) {
      return myCachedIsValidBase;
    }

    PsiElement element = mySmartPsiElementPointer.getElement();
    myCachedIsValidBase = element != null && isApplicableElement(element) && element.isValid();
    return myCachedIsValidBase;
  }

  @Override
  protected JTree getCurrentTree() {
    @Nls String currentViewType = getCurrentViewType();
    return currentViewType == null ? null : myType2Sheet.get(currentViewType).myTree;
  }

  protected final @Nls String getCurrentViewType() {
    Sheet sheet = myCurrentSheet.get();
    return sheet == null ? null : sheet.myType;
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    super.uiDataSnapshot(sink);
    sink.set(HIERARCHY_BROWSER, this);
    sink.set(PlatformCoreDataKeys.HELP_ID, HELP_ID);
  }

  @Override
  public void dispose() {
    disposeAllSheets();
    super.dispose();
  }

  private void disposeAllSheets() {
    for (Sheet sheet : myType2Sheet.values()) {
      disposeSheet(sheet);
    }
  }

  private void disposeSheet(@NotNull Sheet sheet) {
    Disposer.dispose(sheet);
    myType2Sheet.put(sheet.myType, new Sheet(sheet.myType, sheet.myTree, sheet.myScope, sheet.myOccurenceNavigator));
  }

  protected void doRefresh(boolean currentBuilderOnly) {
    ThreadingAssertions.assertEventDispatchThread();

    if (currentBuilderOnly) LOG.assertTrue(getCurrentViewType() != null);

    if (!isValidBase() || isDisposed()) return;

    if (getCurrentBuilder() == null) return; // seems like we are in the middle of refresh already

    @Nls String currentViewType = getCurrentViewType();
    List<Object> pathsToExpand = new ArrayList<>();
    List<Object> selectionPaths = new ArrayList<>();
    if (currentViewType != null) {
      Sheet sheet = myType2Sheet.get(currentViewType);
      DefaultMutableTreeNode root = (DefaultMutableTreeNode)sheet.myAsyncTreeModel.getRoot();
      TreeBuilderUtil.storePaths(sheet.myTree, root, pathsToExpand, selectionPaths, true);
    }

    PsiElement element = mySmartPsiElementPointer.getElement();
    if (element == null || !isApplicableElement(element)) {
      return;
    }
    if (currentBuilderOnly) {
      Sheet sheet = myType2Sheet.get(currentViewType);
      disposeSheet(sheet);
    }
    else {
      disposeAllSheets();
    }
    setHierarchyBase(element);
    validate();
    ApplicationManager.getApplication().invokeLater(() -> {
      changeView(currentViewType);
      for (Object p : pathsToExpand) {
        expandLater(getCurrentTree(), (HierarchyNodeDescriptor)p);
      }

      selectLater(getCurrentTree(), (List)selectionPaths);
    }, __-> isDisposed());
  }

  protected String getCurrentScopeType() {
    String currentViewType = getCurrentViewType();
    return currentViewType == null ? null : myType2Sheet.get(currentViewType).myScope;
  }

  protected final class AlphaSortAction extends ToggleAction {
    public AlphaSortAction() {
      super(PlatformEditorBundle.messagePointer("action.sort.alphabetically"), PlatformEditorBundle.messagePointer("action.sort.alphabetically"),
            AllIcons.ObjectBrowser.Sorted);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent event) {
      return HierarchyBrowserManager.getSettings(myProject).SORT_ALPHABETICALLY;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent event, boolean flag) {
      HierarchyBrowserManager.getSettings(myProject).SORT_ALPHABETICALLY = flag;
      Comparator<NodeDescriptor<?>> comparator = getComparator();
      myType2Sheet.values().stream().map(s->s.myStructureTreeModel).filter(m-> m != null).forEach(m->m.setComparator(comparator));
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
      event.getUpdateSession().compute(this, "AlphaSortAction.super.update", ActionUpdateThread.EDT, () -> {
        super.update(event);
        return null;
      });
      Presentation presentation = event.getPresentation();
      presentation.setEnabled(isValidBase());
    }
  }

  protected static class BaseOnThisElementAction extends AnAction {
    private final LanguageExtension<HierarchyProvider> myProviderLanguageExtension;

    protected BaseOnThisElementAction(@NotNull LanguageExtension<HierarchyProvider> providerLanguageExtension) {
      myProviderLanguageExtension = providerLanguageExtension;
    }

    @Override
    public final void actionPerformed(@NotNull AnActionEvent event) {
      HierarchyBrowserBaseEx browser = event.getData(HIERARCHY_BROWSER);
      if (browser == null) return;

      PsiElement selectedElement = browser.getSelectedElement(event.getDataContext());
      if (selectedElement == null || !browser.isApplicableElementForBaseOn(selectedElement)) return;

      @Nls String currentViewType = browser.getCurrentViewType();
      Disposer.dispose(browser);
      HierarchyProvider provider = BrowseHierarchyActionBase.findProvider(
        myProviderLanguageExtension, selectedElement, selectedElement.getContainingFile(), event.getDataContext());
      if (provider != null) {
        HierarchyBrowserBaseEx newBrowser = (HierarchyBrowserBaseEx)BrowseHierarchyActionBase.createAndAddToPanel(
          selectedElement.getProject(), provider, selectedElement);
        ApplicationManager.getApplication().invokeLater(() -> newBrowser.changeView(correctViewType(browser, currentViewType)), __ -> newBrowser.isDisposed());
      }
    }

    protected @Nls String correctViewType(@NotNull HierarchyBrowserBaseEx browser, @Nls String viewType) {
      return viewType;
    }

    @Override
    public final void update(@NotNull AnActionEvent event) {
      Presentation presentation = event.getPresentation();

      HierarchyBrowserBaseEx browser = event.getData(HIERARCHY_BROWSER);
      if (browser == null) {
        presentation.setEnabledAndVisible(false);
        return;
      }

      presentation.setVisible(true);

      PsiElement selectedElement = browser.getSelectedElement(event.getDataContext());
      if (selectedElement == null || !browser.isApplicableElementForBaseOn(selectedElement)) {
        presentation.setEnabledAndVisible(false);
      }
      else {
        String typeName = ElementDescriptionUtil.getElementDescription(selectedElement, UsageViewTypeLocation.INSTANCE);
        if (StringUtil.isNotEmpty(typeName)) {
          presentation.setText(IdeBundle.messagePointer("action.base.on.this.0", StringUtil.capitalize(typeName)));
        }
        presentation.setEnabled(isEnabled(browser, selectedElement));
      }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    protected boolean isEnabled(@NotNull HierarchyBrowserBaseEx browser, @NotNull PsiElement element) {
      return !element.equals(browser.mySmartPsiElementPointer.getElement()) && element.isValid();
    }
  }

  private final class RefreshAction extends com.intellij.ide.actions.RefreshAction {
    RefreshAction() {
      super(IdeBundle.messagePointer("action.refresh"), IdeBundle.messagePointer("action.refresh"), AllIcons.Actions.Refresh);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      doRefresh(false);
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
      Presentation presentation = event.getPresentation();
      presentation.setEnabled(isValidBase());
    }
  }

  private List<NamedScope> getValidScopes() {
    List<NamedScope> result = new ArrayList<>();
    result.add(ProjectProductionScope.INSTANCE);
    result.add(TestsScope.INSTANCE);
    result.add(CustomScopesProviderEx.getAllScope());
    result.add(new NamedScope(SCOPE_CLASS, () -> LangBundle.message("this.class.scope.name"), AllIcons.Ide.LocalScope, null));
    result.add(new NamedScope(SCOPE_MODULE, () -> LangBundle.message("this.module.scope.name"), AllIcons.Ide.LocalScope, null));

    NamedScopesHolder[] holders = NamedScopesHolder.getAllNamedScopeHolders(myProject);
    for (NamedScopesHolder holder : holders) {
      NamedScope[] scopes = holder.getEditableScopes(); //predefined scopes already included
      Collections.addAll(result, scopes);
    }
    return result;
  }

  public class ChangeScopeAction extends ComboBoxAction {
    @Override
    public final void update(@NotNull AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      Project project = e.getProject();
      if (project == null) return;
      presentation.setEnabled(isEnabled());
      //noinspection HardCodedStringLiteral
      String scopeType = getCurrentScopeType();
      presentation.setText(myI18nMap.getOrDefault(scopeType, () -> scopeType));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    protected boolean isEnabled(){
      return true;
    }

    @Override
    protected final @NotNull DefaultActionGroup createPopupActionGroup(@NotNull JComponent button, @NotNull DataContext context) {
      DefaultActionGroup group = new DefaultActionGroup();

      for(NamedScope namedScope: getValidScopes()) {
        group.add(new MenuAction(namedScope));
      }

      group.add(new ConfigureScopesAction());

      return group;
    }

    private void selectScope(@NotNull String scopeType) {
      myType2Sheet.get(getCurrentViewType()).myScope =  scopeType;
      HierarchyBrowserManager.getSettings(myProject).SCOPE = scopeType;

      // invokeLater is called to update state of button before long tree building operation
      // scope is kept per type so other builders don't need to be refreshed
      ApplicationManager.getApplication().invokeLater(() -> doRefresh(true), __ -> isDisposed());
    }

    @Override
    public final @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
      JPanel panel = new JPanel(new GridBagLayout());
      panel.add(new JLabel(IdeBundle.message("label.scope")),
                new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.BOTH, JBUI.insetsLeft(5), 0, 0));
      panel.add(super.createCustomComponent(presentation, place),
                new GridBagConstraints(1, 0, 1, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.BOTH, JBInsets.emptyInsets(), 0, 0));
      if (ExperimentalUI.isNewUI()) {
        UIUtil.setBackgroundRecursively(panel, JBUI.CurrentTheme.ToolWindow.background());
      }
      return panel;
    }

    private final class MenuAction extends AnAction {
      private final String myScopeType;

      MenuAction(NamedScope namedScope) {
        super(namedScope.getPresentableName());
        myScopeType = namedScope.getScopeId();
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        selectScope(myScopeType);
      }
    }

    private final class ConfigureScopesAction extends AnAction {
      private ConfigureScopesAction() {
        super(ActionsBundle.messagePointer("action.ConfigureScopesAction.text"));
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        EditScopesDialog.showDialog(myProject, null);
        if (getValidScopes().stream().anyMatch(scope -> scope.getScopeId().equals(getCurrentScopeType()))) {
          selectScope(SCOPE_ALL);
        }
      }
    }
  }
}