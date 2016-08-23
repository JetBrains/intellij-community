/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.dependencyAnalysis;

import com.intellij.ProjectTopics;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleSourceOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ui.CellAppearanceEx;
import com.intellij.openapi.roots.ui.OrderEntryAppearanceService;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.PathUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * The classpath details component
 */
public class AnalyzeDependenciesComponent extends MasterDetailsComponent {
  /**
   * Data key for order path element
   */
  public static DataKey<ModuleDependenciesAnalyzer.OrderPathElement> ORDER_PATH_ELEMENT_KEY = DataKey.create("ORDER_PATH_ELEMENT");
  /**
   * The module being analyzed
   */
  private final Module myModule;
  /**
   * The settings for UI mode
   */
  private final AnalyzeDependenciesSettings mySettings;
  /**
   * The cached analyzed classpaths for this module
   */
  private final HashMap<Pair<ClasspathType, Boolean>, ModuleDependenciesAnalyzer> myClasspaths =
    new HashMap<>();

  /**
   * The message bus connection to use
   */
  private MessageBusConnection myMessageBusConnection;

  /**
   * The constructor
   *
   * @param module the module to analyze
   */
  public AnalyzeDependenciesComponent(Module module) {
    myModule = module;
    mySettings = AnalyzeDependenciesSettings.getInstance(myModule.getProject());
    initTree();
    init();
    getSplitter().setProportion(0.3f);
    myMessageBusConnection = myModule.getProject().getMessageBus().connect();
    myMessageBusConnection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        myClasspaths.clear();
        updateTree();
      }
    });
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void disposeUIResources() {
    if (myMessageBusConnection != null) {
      myMessageBusConnection.disconnect();
    }
  }

  /**
   * Initialize components
   */
  private void init() {
    myTree.setCellRenderer(new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        //if(getBackground() == null) {
        //  setBackground(UIUtil.getTreeTextBackground());
        //}
        if (value instanceof MyNode && !(value instanceof MyRootNode)) {
          final MyNode node = (MyNode)value;
          PathNode<?> n = (PathNode<?>)node.getUserObject();
          CellAppearanceEx a = n.getAppearance(selected, node.isDisplayInBold());
          a.customize(this);
        }
      }
    });
    myTree.setShowsRootHandles(false);
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    reloadTree();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected ArrayList<AnAction> createActions(boolean fromPopup) {
    if (!fromPopup) {
      ArrayList<AnAction> rc = new ArrayList<>();
      rc.add(new ClasspathTypeAction());
      rc.add(new SdkFilterAction());
      rc.add(new UrlModeAction());
      return rc;
    }
    else {
      return super.createActions(fromPopup);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void processRemovedItems() {
    // no remove action so far
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected boolean wasObjectStored(Object editableObject) {
    // no modifications so far
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Nls
  @Override
  public String getDisplayName() {
    return "Classpath Details";
  }

  /**
   * Reload tree
   */
  public void reloadTree() {
    myRoot.removeAllChildren();
    ModuleDependenciesAnalyzer a = getAnalyzer();
    if (mySettings.isUrlMode()) {
      for (ModuleDependenciesAnalyzer.UrlExplanation urlExplanation : a.getUrls()) {
        myRoot.add(new MyNode(new UrlNode(urlExplanation)));
      }
    }
    else {
      for (ModuleDependenciesAnalyzer.OrderEntryExplanation explanation : a.getOrderEntries()) {
        myRoot.add(new MyNode(new OrderEntryNode(explanation)));
      }
    }
    ((DefaultTreeModel)myTree.getModel()).reload(myRoot);
  }

  /**
   * @return the analyzer for the current settings
   */
  public ModuleDependenciesAnalyzer getAnalyzer() {
    final Pair<ClasspathType, Boolean> key = Pair.create(getClasspathType(), mySettings.isSdkIncluded());
    ModuleDependenciesAnalyzer a = myClasspaths.get(key);
    if (a == null) {
      a = new ModuleDependenciesAnalyzer(myModule, !mySettings.isTest(), !mySettings.isRuntime(), mySettings.isSdkIncluded());
      myClasspaths.put(key, a);
    }
    return a;
  }

  /**
   * @return the current classpath type from settings
   */
  private ClasspathType getClasspathType() {
    return mySettings.isRuntime() ?
           (mySettings.isTest() ? ClasspathType.TEST_RUNTIME : ClasspathType.PRODUCTION_RUNTIME) :
           (mySettings.isTest() ? ClasspathType.TEST_COMPILE : ClasspathType.PRODUCTION_COMPILE);
  }


  /**
   * Schedule updating the tree
   */
  void updateTree() {
    // TODO make loading in the background if there will be significant delays on big projects
    reloadTree();
  }

  /**
   * The action that allows navigating to the path element
   */
  static class NavigateAction extends DumbAwareAction {

    /**
     * The constructor
     */
    NavigateAction() {
      super("Navigate to ...", "Navigate to place where path element is defined", null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void actionPerformed(AnActionEvent e) {
      final Module module = e.getData(LangDataKeys.MODULE);
      if (module == null) {
        return;
      }
      final ModuleDependenciesAnalyzer.OrderPathElement element = e.getData(ORDER_PATH_ELEMENT_KEY);
      if (element != null && element instanceof ModuleDependenciesAnalyzer.OrderEntryPathElement) {
        final ModuleDependenciesAnalyzer.OrderEntryPathElement o = (ModuleDependenciesAnalyzer.OrderEntryPathElement)element;
        final OrderEntry entry = o.entry();
        final Module m = entry.getOwnerModule();
        ProjectStructureConfigurable.getInstance(module.getProject()).selectOrderEntry(m, entry);
      }
    }
  }

  /**
   * Base class for nodes
   *
   * @param <T> the actual explanation type
   */
  abstract class PathNode<T extends ModuleDependenciesAnalyzer.Explanation> extends NamedConfigurable<T> implements DataProvider {
    /**
     * The cut off length, after which URLs are not shown (only suffix)
     */
    public static final int CUTOFF_LENGTH = 80;

    /**
     * The explanation
     */
    protected final T myExplanation;
    /**
     * The tree with explanation
     */
    private Tree myExplanationTree;

    /**
     * The constructor
     *
     * @param explanation the wrapped explanation
     */
    public PathNode(T explanation) {
      myExplanation = explanation;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T getEditableObject() {
      return myExplanation;
    }

    /**
     * @return a created tree component (to be used as
     */
    private JComponent createTreeComponent() {
      myExplanationTree = new Tree(new DefaultTreeModel(buildTree()));
      myExplanationTree.setRootVisible(false);
      myExplanationTree.setCellRenderer(new ExplanationTreeRenderer());
      DataManager.registerDataProvider(myExplanationTree, this);
      TreeUtil.expandAll(myExplanationTree);
      final NavigateAction navigateAction = new NavigateAction();
      navigateAction.registerCustomShortcutSet(new CustomShortcutSet(CommonShortcuts.DOUBLE_CLICK_1.getShortcuts()[0]), myExplanationTree);
      DefaultActionGroup group = new DefaultActionGroup();
      group.addAction(navigateAction);
      PopupHandler.installUnknownPopupHandler(myExplanationTree, group, ActionManager.getInstance());
      return new JBScrollPane(myExplanationTree);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getData(@NonNls String dataId) {
      if (CommonDataKeys.PROJECT.is(dataId)) {
        return myModule.getProject();
      }
      if (LangDataKeys.MODULE.is(dataId)) {
        return myModule;
      }
      TreePath selectionPath = myExplanationTree.getSelectionPath();
      DefaultMutableTreeNode node = selectionPath == null ? null : (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
      Object o = node == null ? null : node.getUserObject();
      if (o instanceof ModuleDependenciesAnalyzer.OrderPathElement) {
        if (ORDER_PATH_ELEMENT_KEY.is(dataId)) {
          return o;
        }
      }
      return null;
    }

    /**
     * Build tree for the dependencies
     *
     * @return a tree model
     */
    private DefaultMutableTreeNode buildTree() {
      DefaultMutableTreeNode root = new DefaultMutableTreeNode("ROOT");
      for (ModuleDependenciesAnalyzer.OrderPath orderPath : myExplanation.paths()) {
        addDependencyPath(root, orderPath, 0);
      }
      return root;
    }

    /**
     * Add the dependency path
     *
     * @param parent    the parent to which path is added
     * @param orderPath the order entry path
     * @param i         the position in the path
     */
    private void addDependencyPath(DefaultMutableTreeNode parent,
                                   ModuleDependenciesAnalyzer.OrderPath orderPath,
                                   int i) {
      if (i >= orderPath.entries().size()) {
        return;
      }
      ModuleDependenciesAnalyzer.OrderPathElement e = orderPath.entries().get(i);
      int sz = parent.getChildCount();
      DefaultMutableTreeNode n;
      if (sz == 0) {
        n = null;
      }
      else {
        n = (DefaultMutableTreeNode)parent.getChildAt(sz - 1);
        if (!n.getUserObject().equals(e)) {
          n = null;
        }
      }
      if (n == null) {
        n = new DefaultMutableTreeNode(e);
        parent.add(n);
      }
      addDependencyPath(n, orderPath, i + 1);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void setDisplayName(String name) {
      // do nothing
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JComponent createOptionsPanel() {
      final JComponent tree = createTreeComponent();
      JPanel panel = new JPanel(new BorderLayout());
      JLabel paths = new JLabel("Available Through Paths:");
      paths.setDisplayedMnemonic('P');
      paths.setLabelFor(tree);
      panel.add(paths, BorderLayout.NORTH);
      panel.add(tree, BorderLayout.CENTER);
      return panel;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void disposeUIResources() {
      //Do nothing
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getHelpTopic() {
      return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isModified() {
      return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void apply() throws ConfigurationException {
      // Do nothing
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reset() {
      //Do nothing
    }

    /**
     * Get appearance for rendering in master list
     *
     * @param selected true if selected
     * @param bold     true if bold
     * @return the result appearance
     */
    public abstract CellAppearanceEx getAppearance(boolean selected, boolean bold);

    /**
     * @retrun the string cut so it would fit the banner (the prefix is dropped)
     */
    protected String suffixForBanner(String p) {
      if (p.length() > CUTOFF_LENGTH) {
        p = "..." + p.substring(p.length() - CUTOFF_LENGTH);
      }
      return p;
    }

    /**
     * @retrun the string cut so it would fit the banner (the suffix is dropped)
     */
    protected String prefixForBanner(String p) {
      if (p.length() > CUTOFF_LENGTH) {
        p = p.substring(0, CUTOFF_LENGTH) + "...";
      }
      return p;
    }
  }

  /**
   * Cell renderer for explanation tree
   */
  static class ExplanationTreeRenderer extends ColoredTreeCellRenderer {

    @Override
    public void customizeCellRenderer(JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      DefaultMutableTreeNode n = (DefaultMutableTreeNode)value;
      final Object userObject = n.getUserObject();
      if (!(userObject instanceof ModuleDependenciesAnalyzer.OrderPathElement)) {
        return;
      }
      ModuleDependenciesAnalyzer.OrderPathElement e = (ModuleDependenciesAnalyzer.OrderPathElement)userObject;
      final CellAppearanceEx appearance = e.getAppearance(selected);
      appearance.customize(this);
    }
  }

  /**
   * The entry node in URL node
   */
  class UrlNode extends PathNode<ModuleDependenciesAnalyzer.UrlExplanation> {

    /**
     * The constructor
     *
     * @param url the wrapped explanation
     */
    public UrlNode(ModuleDependenciesAnalyzer.UrlExplanation url) {
      super(url);
      setNameFieldShown(false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CellAppearanceEx getAppearance(boolean selected, final boolean isBold) {
      return new CellAppearanceEx() {
        @Override
        public void customize(@NotNull SimpleColoredComponent component) {
          component.setIcon(getIcon());
          final Font font = UIUtil.getTreeFont();
          if (isBold) {
            component.setFont(font.deriveFont(Font.BOLD));
          }
          else {
            component.setFont(font.deriveFont(Font.PLAIN));
          }
          final String p = PathUtil.toPresentableUrl(getEditableObject().url());
          component.append(PathUtil.getFileName(p),
                           isBold ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
          component.append(" (" + PathUtil.getParentPath(p) + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }

        @Override
        public void customize(@NotNull final HtmlListCellRenderer renderer) {
          throw new UnsupportedOperationException("Rendering in combo box not supported yet.");
        }

        @NotNull
        @Override
        public String getText() {
          return getDisplayName();
        }
      };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBannerSlogan() {
      final VirtualFile f = myExplanation.getLocalFile();
      String p = f == null ? myExplanation.url() : f.getPath();
      p = suffixForBanner(p);
      return p;
    }

    /**
     * {@inheritDoc}
     */
    private Icon getIcon() {
      return myExplanation.getIcon();
    }

    /**
     * {@inheritDoc}
     */
    @Nls
    @Override
    public String getDisplayName() {
      return myExplanation.url();
    }
  }

  /**
   * The wrapper for order entries
   */
  class OrderEntryNode extends PathNode<ModuleDependenciesAnalyzer.OrderEntryExplanation> {

    /**
     * The constructor
     *
     * @param orderEntryExplanation the explanation to wrap
     */
    public OrderEntryNode(ModuleDependenciesAnalyzer.OrderEntryExplanation orderEntryExplanation) {
      super(orderEntryExplanation);
      setNameFieldShown(false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CellAppearanceEx getAppearance(boolean selected, final boolean isBold) {
      if (myExplanation.entry() instanceof ModuleSourceOrderEntry) {
        ModuleSourceOrderEntry e = (ModuleSourceOrderEntry)myExplanation.entry();
        if (e.getOwnerModule() == myModule) {
          return new CellAppearanceEx() {
            @Override
            public void customize(@NotNull SimpleColoredComponent component) {
              component.setIcon(ModuleType.get(myModule).getIcon());
              component.append("<This Module>", SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
            }

            @Override
            public void customize(@NotNull final HtmlListCellRenderer renderer) {
              throw new UnsupportedOperationException("Rendering in combo box not supported yet.");
            }

            @NotNull
            @Override
            public String getText() {
              return "<This Module>";
            }
          };
        }
        else {
          return OrderEntryAppearanceService.getInstance().forModule(e.getOwnerModule());
        }
      }
      return OrderEntryAppearanceService.getInstance().forOrderEntry(myModule.getProject(), myExplanation.entry(), selected);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBannerSlogan() {
      if (myExplanation.entry() instanceof ModuleSourceOrderEntry) {
        ModuleSourceOrderEntry e = (ModuleSourceOrderEntry)myExplanation.entry();
        return prefixForBanner("Module " + e.getOwnerModule().getName());
      }
      else {
        final String p = myExplanation.entry().getPresentableName() + " in module " + myExplanation.entry().getOwnerModule().getName();
        return suffixForBanner(p);
      }
    }

    /**
     * {@inheritDoc}
     */
    @Nls
    @Override
    public String getDisplayName() {
      return myExplanation.entry().getPresentableName();
    }
  }

  /**
   * The action that allows including and excluding SDK entries from analysis
   */
  private class SdkFilterAction extends ToggleAction {

    /**
     * The constructor
     */
    public SdkFilterAction() {
      super("Include SDK", "If selected, the SDK classes are included", AllIcons.General.Jdk);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSelected(AnActionEvent e) {
      return mySettings.isSdkIncluded();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      mySettings.setIncludeSdk(state);
      updateTree();
    }
  }

  /**
   * The action that allows switching class path between URL and order entry modes
   */
  private class UrlModeAction extends ToggleAction {
    /**
     * The constructor
     */
    public UrlModeAction() {
      super("Use URL mode", "If selected, the URLs are displayed, otherwise order entries", AllIcons.Nodes.PpFile);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSelected(AnActionEvent e) {
      return mySettings.isUrlMode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      mySettings.setUrlMode(state);
      updateTree();
    }
  }

  /**
   * Classpath type action for the analyze classpath
   */
  private class ClasspathTypeAction extends ComboBoxAction {
    /**
     * The filter action group
     */
    DefaultActionGroup myItems;

    /**
     * {@inheritDoc}
     */
    @NotNull
    @Override
    protected DefaultActionGroup createPopupActionGroup(JComponent button) {
      if (myItems == null) {
        myItems = new DefaultActionGroup(null, true);
        for (final ClasspathType classpathType : ClasspathType.values()) {
          myItems.addAction(new DumbAwareAction(classpathType.getDescription()) {
            @Override
            public void actionPerformed(AnActionEvent e) {
              mySettings.setRuntime(classpathType.isRuntime());
              mySettings.setTest(classpathType.isTest());
              updateTree();
            }
          });
        }
      }
      return myItems;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void update(AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      updateText(presentation);
    }

    /**
     * Update the text for the combobox
     *
     * @param presentation the presentaiton to update
     */
    private void updateText(Presentation presentation) {
      final ClasspathType classpathType = getClasspathType();
      String t = classpathType.getDescription();
      presentation.setText(t);
    }
  }

  /**
   * The enumeration type that represents classpath entry filter
   */
  private static enum ClasspathType {
    /**
     * The production compile mode
     */
    PRODUCTION_COMPILE(false, false, "Production Compile"),
    /**
     * The production runtime mode
     */
    PRODUCTION_RUNTIME(false, true, "Production Runtime"),
    /**
     * The test runtime mode
     */
    TEST_RUNTIME(true, true, "Test Runtime"),
    /**
     * The test compile mode
     */
    TEST_COMPILE(true, false, "Test Compile");

    /**
     * true, if test mode
     */
    final private boolean myIsTest;
    /**
     * true, if runtime mode
     */
    final private boolean myIsRuntime;
    /**
     * The description text
     */
    final private String myDescription;

    /**
     * The constructor
     *
     * @param isTest      true if the test mode
     * @param isRuntime   true if the runtime ode
     * @param description the description text
     */
    ClasspathType(boolean isTest, boolean isRuntime, String description) {
      myIsTest = isTest;
      myIsRuntime = isRuntime;
      myDescription = description;
    }

    /**
     * @return true if the test mode
     */
    public boolean isTest() {
      return myIsTest;
    }

    /**
     * @return true if the runtime mode
     */
    public boolean isRuntime() {
      return myIsRuntime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
      return myDescription;
    }

    /**
     * @return the description for the entry
     */
    public String getDescription() {
      return myDescription;
    }
  }
}
