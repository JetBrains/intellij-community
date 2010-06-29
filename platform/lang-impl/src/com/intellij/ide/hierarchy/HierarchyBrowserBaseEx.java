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

package com.intellij.ide.hierarchy;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.ide.OccurenceNavigatorSupport;
import com.intellij.ide.dnd.DnDAction;
import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDSource;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.awt.*;
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

  private final List<Runnable> myRunOnDisposeList = new ArrayList<Runnable>();
  private final HashMap<String, OccurenceNavigator> myOccurrenceNavigators = new HashMap<String, OccurenceNavigator>();

  private static final OccurenceNavigator EMPTY_NAVIGATOR = new OccurenceNavigator() {
    public boolean hasNextOccurence() {
      return false;
    }

    public boolean hasPreviousOccurence() {
      return false;
    }

    public OccurenceInfo goNextOccurence() {
      return null;
    }

    public OccurenceInfo goPreviousOccurence() {
      return null;
    }

    public String getNextOccurenceActionName() {
      return "";
    }

    public String getPreviousOccurenceActionName() {
      return "";
    }
  };
  public static final String SCOPE_PROJECT = IdeBundle.message("hierarchy.scope.project");
  public static final String SCOPE_ALL = IdeBundle.message("hierarchy.scope.all");
  public static final String SCOPE_TEST = IdeBundle.message("hierarchy.scope.test");
  public static final String SCOPE_CLASS = IdeBundle.message("hierarchy.scope.this.class");
  protected final Map<String, String> myType2ScopeMap = new HashMap<String, String>();

  public HierarchyBrowserBaseEx(final Project project, final PsiElement element) {
    super(project);

    setHierarchyBase(element);

    myCardLayout = new CardLayout();
    myTreePanel = new JPanel(myCardLayout);

    createTrees(myType2TreeMap);

    for (String type : myType2TreeMap.keySet()) {
      myType2ScopeMap.put(type, SCOPE_ALL);
    }

    final Enumeration<String> keys = myType2TreeMap.keys();
    while (keys.hasMoreElements()) {
      final String key = keys.nextElement();
      final JTree tree = myType2TreeMap.get(key);
      myOccurrenceNavigators.put(key, new OccurenceNavigatorSupport(tree) {
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

        public String getNextOccurenceActionName() {
          return HierarchyBrowserBaseEx.this.getNextOccurenceActionNameImpl();
        }

        public String getPreviousOccurenceActionName() {
          return HierarchyBrowserBaseEx.this.getPrevOccurenceActionNameImpl();
        }
      });
      myTreePanel.add(new JBScrollPane(tree), key);
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
    if (dndAware) {
      tree = new DnDAwareTree(new DefaultTreeModel(new DefaultMutableTreeNode("")));
      if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
        ((DnDAwareTree)tree).enableDnd(this);
        DnDManager.getInstance().registerSource(new DnDSource() {
          public boolean canStartDragging(final DnDAction action, final Point dragOrigin) {
            return getSelectedElements().length > 0;
          }

          public DnDDragStartBean startDragging(final DnDAction action, final Point dragOrigin) {
            return new DnDDragStartBean(new AbstractProjectViewPane.TransferableWrapper() {
              public TreeNode[] getTreeNodes() {
                return tree.getSelectedNodes(TreeNode.class, null);
              }

              public PsiElement[] getPsiElements() {
                return getSelectedElements();
              }
            });
          }

          public Pair<Image, Point> createDraggedImage(final DnDAction action, final Point dragOrigin) {
            return null;
          }

          public void dragDropEnd() {
          }

          public void dropActionChanged(final int gestureModifiers) {
          }
        }, tree);
      }
    }
    else {
      tree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode("")));
    }
    configureTree(tree);
    EditSourceOnDoubleClickHandler.install(tree);
    myRefreshAction.registerShortcutOn(tree);
    myRunOnDisposeList.add(new Runnable() {
      public void run() {
        myRefreshAction.unregisterCustomShortcutSet(tree);
      }
    });

    return tree;
  }

  protected void setHierarchyBase(final PsiElement element) {
    mySmartPsiElementPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(element);
  }

  private void restoreCursor() {
    myAlarm.cancelAllRequests();
    setCursor(Cursor.getDefaultCursor());
  }

  private void setWaitCursor() {
    myAlarm.addRequest(new Runnable() {
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
    else {
      return null;
    }
  }

  @Override
  protected void appendActions(@NotNull DefaultActionGroup actionGroup, String helpID) {
    prependActions(actionGroup);
    actionGroup.add(myRefreshAction);
    super.appendActions(actionGroup, helpID);
  }

  protected void prependActions(final DefaultActionGroup actionGroup) {
  }

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

  public boolean hasPreviousOccurence() {
    return getOccurrenceNavigator().hasPreviousOccurence();
  }

  public OccurenceInfo goNextOccurence() {
    return getOccurrenceNavigator().goNextOccurence();
  }

  public OccurenceInfo goPreviousOccurence() {
    return getOccurrenceNavigator().goPreviousOccurence();
  }

  public String getNextOccurenceActionName() {
    return getOccurrenceNavigator().getNextOccurenceActionName();
  }

  public String getPreviousOccurenceActionName() {
    return getOccurrenceNavigator().getPreviousOccurenceActionName();
  }

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

  protected JTree getCurrentTree() {
    if (myCurrentViewType == null) return null;
    return myType2TreeMap.get(myCurrentViewType);
  }

  public String getCurrentViewType() {
    return myCurrentViewType;
  }

  private PsiElement[] getSelectedElements() {
    HierarchyNodeDescriptor[] descriptors = getSelectedDescriptors();
    ArrayList<PsiElement> elements = new ArrayList<PsiElement>();
    for (HierarchyNodeDescriptor descriptor : descriptors) {
      PsiElement element = getElementFromDescriptor(descriptor);
      if (element != null) elements.add(element);
    }
    return elements.toArray(new PsiElement[elements.size()]);
  }

  public Object getData(final String dataId) {
    if (getBrowserDataKey().equals(dataId)) {
      return this;
    }
    else if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return HELP_ID;
    }
    return super.getData(dataId);
  }

  public void dispose() {
    final Collection<HierarchyTreeBuilder> builders = myBuilders.values();
    for (final HierarchyTreeBuilder builder : builders) {
      Disposer.dispose(builder);
    }
    for (final Runnable aRunOnDisposeList : myRunOnDisposeList) {
      aRunOnDisposeList.run();
    }
    myRunOnDisposeList.clear();
    myBuilders.clear();
  }

  protected void doRefresh(boolean currentBuilderOnly) {
    if (currentBuilderOnly) LOG.assertTrue(myCurrentViewType != null);

    if (!isValidBase()) return;

    if (getCurrentBuilder() == null) return; // seems like we are in the middle of refresh already

    final Ref<Object> storedInfo = new Ref<Object>();
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
      public void run() {
        changeView(currentViewType);
        if (storedInfo != null) {
          final HierarchyTreeBuilder builder = getCurrentBuilder();
          builder.restoreExpandedAndSelectedInfo(storedInfo.get());
        }
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
            IconLoader.getIcon("/objectBrowser/sorted.png"));
    }

    public final boolean isSelected(final AnActionEvent event) {
      return HierarchyBrowserManager.getInstance(myProject).getState().SORT_ALPHABETICALLY;
    }

    public final void setSelected(final AnActionEvent event, final boolean flag) {
      final HierarchyBrowserManager hierarchyBrowserManager = HierarchyBrowserManager.getInstance(myProject);
      hierarchyBrowserManager.getState().SORT_ALPHABETICALLY = flag;
      final Comparator<NodeDescriptor> comparator = getComparator();
      final Collection<HierarchyTreeBuilder> builders = myBuilders.values();
      for (final HierarchyTreeBuilder builder : builders) {
        builder.setNodeDescriptorComparator(comparator);
      }
    }

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
        public void run() {
          browser.changeView(correctViewType(browser, currentViewType));
        }
      });
    }

    protected String correctViewType(HierarchyBrowserBaseEx browser, String viewType) {
      return viewType;
    }

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

  protected class RefreshAction extends com.intellij.ide.actions.RefreshAction {
    public RefreshAction() {
      super(IdeBundle.message("action.refresh"), IdeBundle.message("action.refresh"), IconLoader.getIcon("/actions/sync.png"));
    }

    public final void actionPerformed(final AnActionEvent e) {
      doRefresh(false);
    }

    public final void update(final AnActionEvent event) {
      final Presentation presentation = event.getPresentation();
      presentation.setEnabled(isValidBase());
    }
  }

  public class ChangeScopeAction extends ComboBoxAction {
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

    @NotNull
    protected final DefaultActionGroup createPopupActionGroup(final JComponent button) {
      final DefaultActionGroup group = new DefaultActionGroup();

      group.add(new MenuAction(SCOPE_PROJECT));
      group.add(new MenuAction(SCOPE_TEST));
      group.add(new MenuAction(SCOPE_ALL));
      group.add(new MenuAction(SCOPE_CLASS));

      final NamedScopesHolder[] holders = NamedScopesHolder.getAllNamedScopeHolders(myProject);
      for (NamedScopesHolder holder : holders) {
        NamedScope[] scopes = holder.getEditableScopes(); //predefined scopes already included
        for (NamedScope scope : scopes) {
          group.add(new MenuAction(scope.getName()));
        }
      }

      return group;
    }

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

      public final void actionPerformed(final AnActionEvent e) {
        myType2ScopeMap.put(myCurrentViewType, myScopeType);

        // invokeLater is called to update state of button before long tree building operation
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            doRefresh(true); // scope is kept per type so other builders doesn't need to be refreshed
          }
        });

      }
    }

  }
}
