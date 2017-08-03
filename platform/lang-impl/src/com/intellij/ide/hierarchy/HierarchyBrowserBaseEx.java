/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.PsiElementNavigatable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.NullableFunction;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.io.File;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;

public abstract class HierarchyBrowserBaseEx extends HierarchyBrowserBase implements OccurenceNavigator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.hierarchy.HierarchyBrowserBaseEx");

  public static final String SCOPE_PROJECT = IdeBundle.message("hierarchy.scope.project");
  public static final String SCOPE_ALL = IdeBundle.message("hierarchy.scope.all");
  public static final String SCOPE_TEST = IdeBundle.message("hierarchy.scope.test");
  public static final String SCOPE_CLASS = IdeBundle.message("hierarchy.scope.this.class");

  private static final String HELP_ID = "reference.toolWindows.hierarchy";

  private static final OccurenceNavigator EMPTY_NAVIGATOR = new OccurenceNavigator() {
    @Override
    public boolean hasNextOccurence() {
      return false;
    }

    @Override
    public boolean hasPreviousOccurence() {
      return false;
    }

    @Override
    public OccurenceInfo goNextOccurence() {
      return null;
    }

    @Override
    public OccurenceInfo goPreviousOccurence() {
      return null;
    }

    @Override
    public String getNextOccurenceActionName() {
      return "";
    }

    @Override
    public String getPreviousOccurenceActionName() {
      return "";
    }
  };

  /** @deprecated use {@link #getBuilderForType(String)} and {@link #getBuilders()} (to be removed in IDEA 2018) */
  @SuppressWarnings({"UseOfObsoleteCollectionType", "DeprecatedIsStillUsed"})
  protected final Hashtable<String, HierarchyTreeBuilder> myBuilders = new Hashtable<>();

  /** @deprecated use {@link #getCurrentViewType()} (to be removed in IDEA 2018) */
  @SuppressWarnings("DeprecatedIsStillUsed")
  protected String myCurrentViewType;

  private final Map<String, HierarchyTreeBuilder> myType2BuilderMap;
  private final Map<String, JTree> myType2TreeMap;
  private final RefreshAction myRefreshAction = new RefreshAction();
  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD,this);
  private SmartPsiElementPointer mySmartPsiElementPointer;
  private final CardLayout myCardLayout;
  private final JPanel myTreePanel;
  private boolean myCachedIsValidBase;
  private final Map<String, OccurenceNavigator> myOccurrenceNavigators = new HashMap<>();
  private final Map<String, String> myType2ScopeMap = new HashMap<>();

  public HierarchyBrowserBaseEx(@NotNull Project project, @NotNull PsiElement element) {
    super(project);

    @SuppressWarnings("deprecation") Map<String, HierarchyTreeBuilder> mapView = myBuilders;
    myType2BuilderMap = mapView;

    setHierarchyBase(element);

    myCardLayout = new CardLayout();
    myTreePanel = new JPanel(myCardLayout);

    Map<String, JTree> type2treeMap = new HashMap<>();
    createTrees(type2treeMap);
    myType2TreeMap = Collections.unmodifiableMap(type2treeMap);

    HierarchyBrowserManager.State state = HierarchyBrowserManager.getSettings(project);
    for (String type : myType2TreeMap.keySet()) {
      myType2ScopeMap.put(type, state.SCOPE != null ? state.SCOPE : SCOPE_ALL);
    }

    for (String key : myType2TreeMap.keySet()) {
      JTree tree = myType2TreeMap.get(key);
      myOccurrenceNavigators.put(key, new OccurenceNavigatorSupport(tree) {
        @Override
        @Nullable
        protected Navigatable createDescriptorForNode(DefaultMutableTreeNode node) {
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
        public String getNextOccurenceActionName() {
          return getNextOccurenceActionNameImpl();
        }

        @Override
        public String getPreviousOccurenceActionName() {
          return getPrevOccurenceActionNameImpl();
        }
      });
      myTreePanel.add(ScrollPaneFactory.createScrollPane(tree), key);
    }

    final JPanel legendPanel = createLegendPanel();
    final JPanel contentPanel;
    if (legendPanel != null) {
      contentPanel = new JPanel(new BorderLayout());
      contentPanel.add(myTreePanel, BorderLayout.CENTER);
      contentPanel.add(legendPanel, BorderLayout.SOUTH);
    }
    else {
      contentPanel = myTreePanel;
    }

    buildUi(createToolbar(getActionPlace(), HELP_ID).getComponent(), contentPanel);
  }

  @Nullable
  protected PsiElement getOpenFileElementFromDescriptor(@NotNull HierarchyNodeDescriptor descriptor) {
    return getElementFromDescriptor(descriptor);
  }

  @Override
  @Nullable
  protected abstract PsiElement getElementFromDescriptor(@NotNull HierarchyNodeDescriptor descriptor);

  @NotNull
  protected abstract String getPrevOccurenceActionNameImpl();

  @NotNull
  protected abstract String getNextOccurenceActionNameImpl();

  protected abstract void createTrees(@NotNull Map<String, JTree> trees);

  @Nullable
  protected abstract JPanel createLegendPanel();

  protected abstract boolean isApplicableElement(@NotNull PsiElement element);

  @Nullable
  protected abstract HierarchyTreeStructure createHierarchyTreeStructure(@NotNull String type, @NotNull PsiElement psiElement);

  @Nullable
  protected abstract Comparator<NodeDescriptor> getComparator();

  @NotNull
  protected abstract String getActionPlace();

  @NotNull
  protected abstract String getBrowserDataKey();

  protected final JTree createTree(boolean dndAware) {
    final Tree tree;
    final NullableFunction<Object, PsiElement> toPsiConverter = o -> {
      if (o instanceof HierarchyNodeDescriptor) {
        return ((HierarchyNodeDescriptor)o).getContainingFile();
      }
      return null;
    };

    if (dndAware) {
      //noinspection Duplicates
      tree = new DnDAwareTree(new DefaultTreeModel(new DefaultMutableTreeNode(""))) {
        @Override
        public void removeNotify() {
          super.removeNotify();
          if (ScreenUtil.isStandardAddRemoveNotify(this))
            myRefreshAction.unregisterCustomShortcutSet(this);
        }

        @Override
        public boolean isFileColorsEnabled() {
          return ProjectViewTree.isFileColorsEnabledFor(this);
        }

        @Override
        public Color getFileColorFor(Object object) {
          return ProjectViewTree.getColorForObject(object, myProject, toPsiConverter);
        }
      };

      if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
        DnDManager.getInstance().registerSource(new DnDSource() {
          @Override
          public boolean canStartDragging(final DnDAction action, final Point dragOrigin) {
            return getSelectedElements().length > 0;
          }

          @Override
          public DnDDragStartBean startDragging(final DnDAction action, final Point dragOrigin) {
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

          @Override
          public Pair<Image, Point> createDraggedImage(final DnDAction action, final Point dragOrigin) {
            return null;
          }

          @Override
          public void dragDropEnd() {
          }

          @Override
          public void dropActionChanged(final int gestureModifiers) {
          }
        }, tree);
      }
    }
    else {
      //noinspection Duplicates
      tree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode("")))  {
        @Override
        public void removeNotify() {
          super.removeNotify();
          if (ScreenUtil.isStandardAddRemoveNotify(this))
            myRefreshAction.unregisterCustomShortcutSet(this);
        }

        @Override
        public boolean isFileColorsEnabled() {
          return ProjectViewTree.isFileColorsEnabledFor(this);
        }

        @Override
        public Color getFileColorFor(Object object) {
          return ProjectViewTree.getColorForObject(object, myProject, toPsiConverter);
        }
      };
    }
    configureTree(tree);
    EditSourceOnDoubleClickHandler.install(tree);
    EditSourceOnEnterKeyHandler.install(tree);
    myRefreshAction.registerShortcutOn(tree);

    return tree;
  }

  protected void setHierarchyBase(@NotNull PsiElement element) {
    mySmartPsiElementPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(element);
  }

  private void restoreCursor() {
    myAlarm.cancelAllRequests();
    setCursor(Cursor.getDefaultCursor());
  }

  private void setWaitCursor() {
    myAlarm.addRequest(() -> setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)), 100);
  }

  public final void changeView(@NotNull final String typeName) {
    changeView(typeName, true);
  }
  public final void changeView(@NotNull final String typeName, boolean requestFocus) {
    setCurrentViewType(typeName);

    final PsiElement element = mySmartPsiElementPointer.getElement();
    if (element == null || !isApplicableElement(element)) {
      return;
    }

    if (myContent != null) {
      final String displayName = getContentDisplayName(typeName, element);
      if (displayName != null) {
        myContent.setDisplayName(displayName);
      }
    }

    myCardLayout.show(myTreePanel, typeName);

    if (!myType2BuilderMap.containsKey(typeName)) {
      try {
        setWaitCursor();
        // create builder
        final JTree tree = myType2TreeMap.get(typeName);
        final DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode(""));
        tree.setModel(model);

        final HierarchyTreeStructure structure = createHierarchyTreeStructure(typeName, element);
        if (structure == null) {
          return;
        }
        final Comparator<NodeDescriptor> comparator = getComparator();
        final HierarchyTreeBuilder builder = new HierarchyTreeBuilder(myProject, tree, model, structure, comparator);

        myType2BuilderMap.put(typeName, builder);
        Disposer.register(this, builder);
        Disposer.register(builder, () -> myType2BuilderMap.remove(typeName));

        final HierarchyNodeDescriptor descriptor = structure.getBaseDescriptor();
        builder.select(descriptor, () -> builder.expand(descriptor, null));
      }
      finally {
        restoreCursor();
      }
    }

    if (requestFocus) {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
        IdeFocusManager.getGlobalInstance().requestFocus(getCurrentTree(), true);
      });
    }
  }

  @SuppressWarnings("deprecation")
  private void setCurrentViewType(String typeName) {
    myCurrentViewType = typeName;
  }

  @Nullable
  protected String getContentDisplayName(@NotNull String typeName, @NotNull PsiElement element) {
    if (element instanceof PsiNamedElement) {
      return MessageFormat.format(typeName, ((PsiNamedElement)element).getName());
    }
    return null;
  }

  @Override
  protected void appendActions(@NotNull DefaultActionGroup actionGroup, String helpID) {
    prependActions(actionGroup);
    actionGroup.add(myRefreshAction);
    super.appendActions(actionGroup, helpID);
  }

  protected void prependActions(final DefaultActionGroup actionGroup) {
  }

  @Override
  public boolean hasNextOccurence() {
    return getOccurrenceNavigator().hasNextOccurence();
  }

  private OccurenceNavigator getOccurrenceNavigator() {
    String currentViewType = getCurrentViewType();
    if (currentViewType != null) {
      OccurenceNavigator navigator = myOccurrenceNavigators.get(currentViewType);
      if (navigator != null) {
        return navigator;
      }
    }
    return EMPTY_NAVIGATOR;
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
  public String getNextOccurenceActionName() {
    return getOccurrenceNavigator().getNextOccurenceActionName();
  }

  @Override
  public String getPreviousOccurenceActionName() {
    return getOccurrenceNavigator().getPreviousOccurenceActionName();
  }

  @Override
  protected HierarchyTreeBuilder getCurrentBuilder() {
    return getBuilderForType(getCurrentViewType());
  }

  protected final HierarchyTreeBuilder getBuilderForType(String viewType) {
    return viewType == null ? null : myType2BuilderMap.get(viewType);
  }

  protected final Iterable<HierarchyTreeBuilder> getBuilders() {
    return Collections.unmodifiableCollection(myType2BuilderMap.values());
  }

  final boolean isValidBase() {
    if (myProject.isDisposed()) return false;
    if (PsiDocumentManager.getInstance(myProject).getUncommittedDocuments().length > 0) {
      return myCachedIsValidBase;
    }

    final PsiElement element = mySmartPsiElementPointer.getElement();
    myCachedIsValidBase = element != null && isApplicableElement(element) && element.isValid();
    return myCachedIsValidBase;
  }

  @Override
  protected JTree getCurrentTree() {
    String currentViewType = getCurrentViewType();
    return currentViewType == null ? null : myType2TreeMap.get(currentViewType);
  }

  @SuppressWarnings("deprecation")
  protected final String getCurrentViewType() {
    return myCurrentViewType;
  }

  @Override
  public Object getData(final String dataId) {
    if (getBrowserDataKey().equals(dataId)) {
      return this;
    }
    if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return HELP_ID;
    }
    return super.getData(dataId);
  }

  private void disposeBuilders() {
    final Collection<HierarchyTreeBuilder> builders = new ArrayList<>(myType2BuilderMap.values());
    for (final HierarchyTreeBuilder builder : builders) {
      Disposer.dispose(builder);
    }
    myType2BuilderMap.clear();
  }

  void doRefresh(boolean currentBuilderOnly) {
    if (currentBuilderOnly) LOG.assertTrue(getCurrentViewType() != null);

    if (!isValidBase()) return;

    if (getCurrentBuilder() == null) return; // seems like we are in the middle of refresh already

    final Ref<Pair<List<Object>, List<Object>>> storedInfo = new Ref<>();
    if (getCurrentViewType() != null) {
      final HierarchyTreeBuilder builder = getCurrentBuilder();
      storedInfo.set(builder.storeExpandedAndSelectedInfo());
    }

    final PsiElement element = mySmartPsiElementPointer.getElement();
    if (element == null || !isApplicableElement(element)) {
      return;
    }
    final String currentViewType = getCurrentViewType();
    if (currentBuilderOnly) {
      Disposer.dispose(getCurrentBuilder());
    }
    else {
      disposeBuilders();
    }
    setHierarchyBase(element);
    validate();
    ApplicationManager.getApplication().invokeLater(() -> {
      changeView(currentViewType);
      final HierarchyTreeBuilder builder = getCurrentBuilder();
      builder.restoreExpandedAndSelectedInfo(storedInfo.get());
    }, __-> isDisposed());
  }

  protected String getCurrentScopeType() {
    String currentViewType = getCurrentViewType();
    return currentViewType == null ? null : myType2ScopeMap.get(currentViewType);
  }

  protected class AlphaSortAction extends ToggleAction {
    public AlphaSortAction() {
      super(IdeBundle.message("action.sort.alphabetically"), IdeBundle.message("action.sort.alphabetically"), AllIcons.ObjectBrowser.Sorted);
    }

    @Override
    public final boolean isSelected(final AnActionEvent event) {
      return HierarchyBrowserManager.getSettings(myProject).SORT_ALPHABETICALLY;
    }

    @Override
    public final void setSelected(final AnActionEvent event, final boolean flag) {
      HierarchyBrowserManager.getSettings(myProject).SORT_ALPHABETICALLY = flag;
      final Comparator<NodeDescriptor> comparator = getComparator();
      for (final HierarchyTreeBuilder builder : getBuilders()) {
        builder.setNodeDescriptorComparator(comparator);
      }
    }

    @Override
    public final void update(@NotNull final AnActionEvent event) {
      super.update(event);
      final Presentation presentation = event.getPresentation();
      presentation.setEnabled(isValidBase());
    }
  }

  static class BaseOnThisElementAction extends AnAction {
    private final String myBrowserDataKey;
    private final LanguageExtension<HierarchyProvider> myProviderLanguageExtension;

    BaseOnThisElementAction(@NotNull String text,
                            @NotNull String browserDataKey,
                            @NotNull LanguageExtension<HierarchyProvider> providerLanguageExtension) {
      super(text);
      myBrowserDataKey = browserDataKey;
      myProviderLanguageExtension = providerLanguageExtension;
    }

    @Override
    public final void actionPerformed(final AnActionEvent event) {
      final DataContext dataContext = event.getDataContext();
      final HierarchyBrowserBaseEx browser = (HierarchyBrowserBaseEx)dataContext.getData(myBrowserDataKey);
      if (browser == null) return;

      final PsiElement selectedElement = browser.getSelectedElement();
      if (selectedElement == null || !browser.isApplicableElement(selectedElement)) return;

      final String currentViewType = browser.getCurrentViewType();
      Disposer.dispose(browser);
      final HierarchyProvider provider = BrowseHierarchyActionBase.findProvider(
        myProviderLanguageExtension, selectedElement, selectedElement.getContainingFile(), event.getDataContext());
      if (provider != null) {
        HierarchyBrowserBaseEx newBrowser = (HierarchyBrowserBaseEx)BrowseHierarchyActionBase.createAndAddToPanel(
          selectedElement.getProject(), provider, selectedElement);
        ApplicationManager.getApplication().invokeLater(() -> newBrowser.changeView(correctViewType(browser, currentViewType)));
      }
    }

    protected String correctViewType(HierarchyBrowserBaseEx browser, String viewType) {
      return viewType;
    }

    @Override
    public final void update(final AnActionEvent event) {
      final Presentation presentation = event.getPresentation();

      final DataContext dataContext = event.getDataContext();
      final HierarchyBrowserBaseEx browser = (HierarchyBrowserBaseEx)dataContext.getData(myBrowserDataKey);
      if (browser == null) {
        presentation.setVisible(false);
        presentation.setEnabled(false);
        return;
      }

      presentation.setVisible(true);

      final PsiElement selectedElement = browser.getSelectedElement();
      if (selectedElement == null || !browser.isApplicableElement(selectedElement)) {
        presentation.setEnabled(false);
        presentation.setVisible(false);
        return;
      }

      presentation.setEnabled(isEnabled(browser, selectedElement));
      String nonDefaultText = getNonDefaultText(browser, selectedElement);
      if (nonDefaultText != null) {
        presentation.setText(nonDefaultText);
      }
    }

    protected boolean isEnabled(@NotNull HierarchyBrowserBaseEx browser, @NotNull PsiElement element) {
      return !element.equals(browser.mySmartPsiElementPointer.getElement()) && element.isValid();
    }

    @Nullable
    protected String getNonDefaultText(@NotNull HierarchyBrowserBaseEx browser, @NotNull PsiElement element) {
      return null;
    }
  }

  private class RefreshAction extends com.intellij.ide.actions.RefreshAction {
    public RefreshAction() {
      super(IdeBundle.message("action.refresh"), IdeBundle.message("action.refresh"), AllIcons.Actions.Refresh);
    }

    @Override
    public final void actionPerformed(final AnActionEvent e) {
      doRefresh(false);
    }

    @Override
    public final void update(final AnActionEvent event) {
      final Presentation presentation = event.getPresentation();
      presentation.setEnabled(isValidBase());
    }
  }

  public class ChangeScopeAction extends ComboBoxAction {
    @Override
    public final void update(final AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      final Project project = e.getProject();
      if (project == null) return;
      presentation.setEnabled(isEnabled());
      presentation.setText(getCurrentScopeType());
    }

    protected boolean isEnabled(){
      return true;
    }

    @Override
    @NotNull
    protected final DefaultActionGroup createPopupActionGroup(final JComponent button) {
      final DefaultActionGroup group = new DefaultActionGroup();

      for(String name: getValidScopeNames()) {
        group.add(new MenuAction(name));
      }
      
      group.add(new ConfigureScopesAction());

      return group;
    }

    private Collection<String> getValidScopeNames() {
      List<String> result = new ArrayList<>();
      result.add(SCOPE_PROJECT);
      result.add(SCOPE_TEST);
      result.add(SCOPE_ALL);
      result.add(SCOPE_CLASS);

      final NamedScopesHolder[] holders = NamedScopesHolder.getAllNamedScopeHolders(myProject);
      for (NamedScopesHolder holder : holders) {
        NamedScope[] scopes = holder.getEditableScopes(); //predefined scopes already included
        for (NamedScope scope : scopes) {
          result.add(scope.getName());
        }
      }
      return result;
    }

    private void selectScope(final String scopeType) {
      myType2ScopeMap.put(getCurrentViewType(), scopeType);
      HierarchyBrowserManager.getSettings(myProject).SCOPE = scopeType;

      // invokeLater is called to update state of button before long tree building operation
      // scope is kept per type so other builders doesn't need to be refreshed
      ApplicationManager.getApplication().invokeLater(() -> doRefresh(true));
    }

    @Override
    public final JComponent createCustomComponent(final Presentation presentation) {
      final JPanel panel = new JPanel(new GridBagLayout());
      panel.add(new JLabel(IdeBundle.message("label.scope")),
                new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.BOTH, JBUI.insetsLeft(5), 0, 0));
      panel.add(super.createCustomComponent(presentation),
                new GridBagConstraints(1, 0, 1, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.BOTH, JBUI.emptyInsets(), 0, 0));
      return panel;
    }

    private final class MenuAction extends AnAction {
      private final String myScopeType;

      public MenuAction(final String scopeType) {
        super(scopeType);
        myScopeType = scopeType;
      }

      @Override
      public final void actionPerformed(final AnActionEvent e) {
        selectScope(myScopeType);
      }
    }
    
    private final class ConfigureScopesAction extends AnAction {
      private ConfigureScopesAction() {
        super("Configure...");
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        EditScopesDialog.showDialog(myProject, null);
        if (!getValidScopeNames().contains(getCurrentScopeType())) {
          selectScope(SCOPE_ALL);
        }
      }
    }
  }
}