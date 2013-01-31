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

package com.intellij.ide.hierarchy;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.ide.OccurenceNavigatorSupport;
import com.intellij.ide.PsiCopyPasteManager;
import com.intellij.ide.dnd.DnDAction;
import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDSource;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.ide.projectView.impl.ProjectViewTree;
import com.intellij.ide.projectView.impl.TransferableWrapper;
import com.intellij.ide.util.scopeChooser.EditScopesDialog;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NonNls;
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

  @NonNls private static final String HELP_ID = "reference.toolWindows.hierarchy";

  protected final Hashtable<String, HierarchyTreeBuilder> myBuilders = new Hashtable<String, HierarchyTreeBuilder>();
  protected final Hashtable<String, JTree> myType2TreeMap = new Hashtable<String, JTree>();

  private final RefreshAction myRefreshAction = new RefreshAction();
  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  private SmartPsiElementPointer mySmartPsiElementPointer;
  private final CardLayout myCardLayout;
  private final JPanel myTreePanel;
  protected String myCurrentViewType;

  private boolean myCachedIsValidBase = false;

  private final HashMap<String, OccurenceNavigator> myOccurrenceNavigators = new HashMap<String, OccurenceNavigator>();

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
  public static final String SCOPE_PROJECT = IdeBundle.message("hierarchy.scope.project");
  public static final String SCOPE_ALL = IdeBundle.message("hierarchy.scope.all");
  public static final String SCOPE_TEST = IdeBundle.message("hierarchy.scope.test");
  public static final String SCOPE_CLASS = IdeBundle.message("hierarchy.scope.this.class");
  protected final Map<String, String> myType2ScopeMap = new HashMap<String, String>();

  public HierarchyBrowserBaseEx(@NotNull Project project, @NotNull PsiElement element) {
    super(project);

    setHierarchyBase(element);

    myCardLayout = new CardLayout();
    myTreePanel = new JPanel(myCardLayout);

    createTrees(myType2TreeMap);

    final HierarchyBrowserManager.State state = HierarchyBrowserManager.getInstance(project).getState();
    for (String type : myType2TreeMap.keySet()) {
      myType2ScopeMap.put(type, state.SCOPE != null ? state.SCOPE : SCOPE_ALL);
    }

    final Enumeration<String> keys = myType2TreeMap.keys();
    while (keys.hasMoreElements()) {
      final String key = keys.nextElement();
      final JTree tree = myType2TreeMap.get(key);
      myOccurrenceNavigators.put(key, new OccurenceNavigatorSupport(tree) {
        @Override
        @Nullable
        protected Navigatable createDescriptorForNode(DefaultMutableTreeNode node) {
          final HierarchyNodeDescriptor descriptor = getDescriptor(node);
          if (descriptor == null) return null;
          PsiElement psiElement = getOpenFileElementFromDescriptor(descriptor);
          if (psiElement == null || !psiElement.isValid()) return null;
          final VirtualFile virtualFile = psiElement.getContainingFile().getVirtualFile();
          if (virtualFile == null) return null;
          return new OpenFileDescriptor(psiElement.getProject(), virtualFile, psiElement.getTextOffset());
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
    final NullableFunction<Object, PsiElement> toPsiConverter = new NullableFunction<Object, PsiElement>() {
      @Override
      public PsiElement fun(Object o) {
        if (o instanceof HierarchyNodeDescriptor) {
          return ((HierarchyNodeDescriptor)o).getContainingFile();
        }
        return null;
      }
    };

    if (dndAware) {
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
    myAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
      }
    }, 100);
  }

  public final void changeView(@NotNull final String typeName) {
    myCurrentViewType = typeName;

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

    if (!myBuilders.containsKey(typeName)) {
      try {
        setWaitCursor();
        // create builder
        final JTree tree = myType2TreeMap.get(typeName);
        final DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode(""));
        tree.setModel(model);

        PsiDocumentManager.getInstance(myProject).commitAllDocuments();
        final HierarchyTreeStructure structure = createHierarchyTreeStructure(typeName, element);
        if (structure == null) {
          return;
        }
        final Comparator<NodeDescriptor> comparator = getComparator();
        final HierarchyTreeBuilder builder = new HierarchyTreeBuilder(myProject, tree, model, structure, comparator);

        myBuilders.put(typeName, builder);

        final HierarchyNodeDescriptor descriptor = structure.getBaseDescriptor();
        builder.select(descriptor, new Runnable() {
          @Override
          public void run() {
            builder.expand(descriptor, null);
          }
        });
      }
      finally {
        restoreCursor();
      }
    }

    getCurrentTree().requestFocus();
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
    if (myCurrentViewType == null) {
      return EMPTY_NAVIGATOR;
    }
    final OccurenceNavigator navigator = myOccurrenceNavigators.get(myCurrentViewType);
    return navigator != null ? navigator : EMPTY_NAVIGATOR;
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
    return myBuilders.get(myCurrentViewType);
  }

  protected final boolean isValidBase() {
    if (PsiDocumentManager.getInstance(myProject).getUncommittedDocuments().length > 0) {
      return myCachedIsValidBase;
    }

    final PsiElement element = mySmartPsiElementPointer.getElement();
    myCachedIsValidBase = element != null && isApplicableElement(element) && element.isValid();
    return myCachedIsValidBase;
  }

  @Override
  protected JTree getCurrentTree() {
    if (myCurrentViewType == null) return null;
    return myType2TreeMap.get(myCurrentViewType);
  }

  public String getCurrentViewType() {
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

  @Override
  public void dispose() {
    final Collection<HierarchyTreeBuilder> builders = myBuilders.values();
    for (final HierarchyTreeBuilder builder : builders) {
      Disposer.dispose(builder);
    }
    myBuilders.clear();
  }

  protected void doRefresh(boolean currentBuilderOnly) {
    if (currentBuilderOnly) LOG.assertTrue(myCurrentViewType != null);

    if (!isValidBase()) return;

    if (getCurrentBuilder() == null) return; // seems like we are in the middle of refresh already

    final Ref<Pair<ArrayList<Object>, ArrayList<Object>>> storedInfo = new Ref<Pair<ArrayList<Object>, ArrayList<Object>>>();
    if (myCurrentViewType != null) {
      final HierarchyTreeBuilder builder = getCurrentBuilder();
      storedInfo.set(builder.storeExpandedAndSelectedInfo());
    }

    final PsiElement element = mySmartPsiElementPointer.getElement();
    if (element == null || !isApplicableElement(element)) {
      return;
    }
    final String currentViewType = myCurrentViewType;

    if (currentBuilderOnly) {
      Disposer.dispose(getCurrentBuilder());
      myBuilders.remove(myCurrentViewType);
    }
    else {
      dispose();
    }
    setHierarchyBase(element);
    validate();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        changeView(currentViewType);
        final HierarchyTreeBuilder builder = getCurrentBuilder();
        builder.restoreExpandedAndSelectedInfo(storedInfo.get());
      }
    });
  }

  protected String getCurrentScopeType() {
    if (myCurrentViewType == null) return null;
    return myType2ScopeMap.get(myCurrentViewType);
  }

  protected class AlphaSortAction extends ToggleAction {
    public AlphaSortAction() {
      super(IdeBundle.message("action.sort.alphabetically"), IdeBundle.message("action.sort.alphabetically"),
            AllIcons.ObjectBrowser.Sorted);
    }

    @Override
    public final boolean isSelected(final AnActionEvent event) {
      return HierarchyBrowserManager.getInstance(myProject).getState().SORT_ALPHABETICALLY;
    }

    @Override
    public final void setSelected(final AnActionEvent event, final boolean flag) {
      final HierarchyBrowserManager hierarchyBrowserManager = HierarchyBrowserManager.getInstance(myProject);
      hierarchyBrowserManager.getState().SORT_ALPHABETICALLY = flag;
      final Comparator<NodeDescriptor> comparator = getComparator();
      final Collection<HierarchyTreeBuilder> builders = myBuilders.values();
      for (final HierarchyTreeBuilder builder : builders) {
        builder.setNodeDescriptorComparator(comparator);
      }
    }

    @Override
    public final void update(final AnActionEvent event) {
      super.update(event);
      final Presentation presentation = event.getPresentation();
      presentation.setEnabled(isValidBase());
    }
  }

  protected static class BaseOnThisElementAction extends AnAction {
    private final String myActionId;
    private final String myBrowserDataKey;

    public BaseOnThisElementAction(String text, String actionId, String browserDataKey) {
      super(text);
      myActionId = actionId;
      myBrowserDataKey = browserDataKey;
    }

    @Override
    public final void actionPerformed(final AnActionEvent event) {
      final DataContext dataContext = event.getDataContext();
      final HierarchyBrowserBaseEx browser = (HierarchyBrowserBaseEx)dataContext.getData(myBrowserDataKey);
      if (browser == null) return;

      final PsiElement selectedElement = browser.getSelectedElement();
      if (selectedElement == null || !browser.isApplicableElement(selectedElement)) return;

      final String currentViewType = browser.myCurrentViewType;
      browser.dispose();
      browser.setHierarchyBase(selectedElement);
      browser.validate();
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          browser.changeView(correctViewType(browser, currentViewType));
        }
      });
    }

    protected String correctViewType(HierarchyBrowserBaseEx browser, String viewType) {
      return viewType;
    }

    @Override
    public final void update(final AnActionEvent event) {
      final Presentation presentation = event.getPresentation();

      registerCustomShortcutSet(ActionManager.getInstance().getAction(myActionId).getShortcutSet(), null);

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
      final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
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
      List<String> result = new ArrayList<String>();
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
      myType2ScopeMap.put(myCurrentViewType, scopeType);
      HierarchyBrowserManager.getInstance(myProject).getState().SCOPE = scopeType;

      // invokeLater is called to update state of button before long tree building operation
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          doRefresh(true); // scope is kept per type so other builders doesn't need to be refreshed
        }
      });
    }

    @Override
    public final JComponent createCustomComponent(final Presentation presentation) {
      final JPanel panel = new JPanel(new GridBagLayout());
      panel.add(new JLabel(IdeBundle.message("label.scope")),
                new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.BOTH, new Insets(0, 5, 0, 0), 0, 0));
      panel.add(super.createCustomComponent(presentation),
                new GridBagConstraints(1, 0, 1, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
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
        if (!getValidScopeNames().contains(myType2ScopeMap.get(myCurrentViewType))) {
          selectScope(SCOPE_ALL);
        }
      }
    }
  }

  // will throw PCE during update when canceled
  public void setProgressIndicator(@NotNull ProgressIndicator indicator) {
    for (HierarchyTreeBuilder builder : myBuilders.values()) {
      builder.setProgressIndicator(indicator);
    }
  }
}
