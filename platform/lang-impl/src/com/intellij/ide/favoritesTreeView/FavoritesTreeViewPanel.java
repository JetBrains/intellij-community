// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.favoritesTreeView;

import com.intellij.icons.AllIcons;
import com.intellij.ide.CopyPasteDelegator;
import com.intellij.ide.IdeView;
import com.intellij.ide.bookmarks.Bookmark;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.ide.favoritesTreeView.actions.*;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.ProjectViewTree;
import com.intellij.ide.projectView.impl.nodes.LibraryGroupElement;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement;
import com.intellij.ide.projectView.impl.nodes.ProjectViewDirectoryHelper;
import com.intellij.ide.ui.customization.CustomizationUtil;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.ide.util.EditorHelper;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockManager;
import com.intellij.ui.docking.DockableContent;
import com.intellij.ui.treeStructure.actions.CollapseAllAction;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * @author anna
 * @author Konstantin Bulenkov
 */
public final class FavoritesTreeViewPanel extends JPanel implements DataProvider, DockContainer {
  private final FavoritesTreeStructure myFavoritesTreeStructure;
  private final FavoritesViewTreeBuilder myBuilder;
  private final CopyPasteDelegator myCopyPasteDelegator;

  public static final DataKey<FavoriteTreeNodeDescriptor[]> CONTEXT_FAVORITES_ROOTS_DATA_KEY = DataKey.create("FavoritesRoot");
  public static final DataKey<DnDAwareTree> FAVORITES_TREE_KEY = DataKey.create("Favorites.Tree");
  public static final DataKey<FavoritesViewTreeBuilder> FAVORITES_TREE_BUILDER_KEY = DataKey.create("Favorites.Tree.Builder");

  public static final DataKey<String> FAVORITES_LIST_NAME_DATA_KEY = DataKey.create("FavoritesListName");
  private final Project myProject;
  final DnDAwareTree myTree;

  private final AutoScrollToSourceHandler myAutoScrollToSourceHandler;

  private final IdeView myIdeView = new MyIdeView();

  public FavoritesTreeViewPanel(@NotNull Project project) {
    super(new BorderLayout());

    myProject = project;

    myFavoritesTreeStructure = new FavoritesTreeStructure(project);
    DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    root.setUserObject(myFavoritesTreeStructure.getRootElement());
    DefaultTreeModel treeModel = new DefaultTreeModel(root);
    myTree = new DnDAwareTree(treeModel) {
      @Override
      public boolean isFileColorsEnabled() {
        return ProjectViewTree.isFileColorsEnabledFor(this);
      }

      @Override
      public Color getFileColorFor(Object object) {
        return ProjectViewTree.getColorForElement(getPsiElement(object));
      }
    };
    myBuilder = new FavoritesViewTreeBuilder(myProject, myTree, treeModel, myFavoritesTreeStructure);
    DockManager.getInstance(project).register(this, project);

    TreeUtil.installActions(myTree);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.setLargeModel(true);
    new TreeSpeedSearch(myTree);
    ToolTipManager.sharedInstance().registerComponent(myTree);
    FavoriteComparator favoritesComparator = new FavoriteComparator();
    FavoritesManager favoriteManager = FavoritesManager.getInstance(myProject);
    myBuilder.setNodeDescriptorComparator((o1, o2) -> {
      if (o1 instanceof FavoriteTreeNodeDescriptor && o2 instanceof FavoriteTreeNodeDescriptor) {
        final FavoritesListNode listNode1 = FavoritesTreeUtil.extractParentList((FavoriteTreeNodeDescriptor)o1);
        final FavoritesListNode listNode2 = FavoritesTreeUtil.extractParentList((FavoriteTreeNodeDescriptor)o2);
        if (listNode1.equals(listNode2)) {
          final Comparator<FavoriteTreeNodeDescriptor> comparator = favoriteManager.getCustomComparator(listNode1.getName());
          if (comparator != null) {
            return comparator.compare((FavoriteTreeNodeDescriptor)o1, (FavoriteTreeNodeDescriptor)o2);
          }
          else {
            return favoritesComparator.compare(o1, o2);
          }
        }
      }
      return o1.getIndex() - o2.getIndex();
    });
    myTree.setCellRenderer(new NodeRenderer() {
      @Override
      public void customizeCellRenderer(@NotNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus);

        if (!(value instanceof DefaultMutableTreeNode)) {
          return;
        }

        final DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
        //only favorites roots to explain
        final Object userObject = node.getUserObject();
        if (userObject instanceof FavoriteTreeNodeDescriptor) {
          final FavoriteTreeNodeDescriptor favoritesTreeNodeDescriptor = (FavoriteTreeNodeDescriptor)userObject;
          AbstractTreeNode<?> treeNode = favoritesTreeNodeDescriptor.getElement();
          FavoritesListProvider provider = FavoritesTreeUtil.getProvider(favoriteManager, favoritesTreeNodeDescriptor);
          if (provider != null) {
            Object o = myBuilder.getUi().getElementFor(value);
            if (o instanceof AbstractTreeNode) {
              o = ((AbstractTreeNode)o).getValue();
            }
            provider.customizeRenderer(this, tree, o, selected, expanded, leaf, row, hasFocus);
            return;
          }
          final ItemPresentation presentation = treeNode.getPresentation();
          String locationString = presentation.getLocationString();
          if (locationString == null &&
              node.getParent() != null &&
              node.getParent().getParent() != null &&
              node.getParent().getParent().getParent() == null) {
            final String location = favoritesTreeNodeDescriptor.getLocation();
            if (location != null && location.length() > 0) {
              append(" (" + location + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
            }
          }
        }
      }
    });
    CustomizationUtil.installPopupHandler(myTree, IdeActions.GROUP_FAVORITES_VIEW_POPUP, ActionPlaces.FAVORITES_VIEW_POPUP);

    EditSourceOnDoubleClickHandler.install(myTree);
    EditSourceOnEnterKeyHandler.install(myTree);
    myCopyPasteDelegator = new CopyPasteDelegator(myProject, this);

    AnActionButton addActionButton = AnActionButton.fromAction(ActionManager.getInstance().getAction("AddNewFavoritesList"));
    addActionButton.getTemplatePresentation().setIcon(CommonActionsPanel.Buttons.ADD.getIcon());
    addActionButton.setShortcut(CommonActionsPanel.getCommonShortcut(CommonActionsPanel.Buttons.ADD));

    AnActionButton editActionButton = AnActionButton.fromAction(ActionManager.getInstance().getAction("EditFavorites"));
    editActionButton.setShortcut(CommonShortcuts.CTRL_ENTER);

    AnActionButton deleteActionButton = new DeleteFromFavoritesAction();
    deleteActionButton.setShortcut(CustomShortcutSet.fromString("DELETE", "BACK_SPACE"));

    //final AnAction exportToTextFileAction = CommonActionsManager.getInstance().createExportToTextFileAction(createTextExporter());
    //AnActionButton exportActionButton = AnActionButton.fromAction(exportToTextFileAction);
    //exportActionButton.setShortcut(exportToTextFileAction.getShortcutSet());

    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myTree)
      .initPosition()
      .disableAddAction().disableRemoveAction().disableDownAction().disableUpAction()
      .addExtraAction(addActionButton)
      .addExtraAction(editActionButton)
      .addExtraAction(deleteActionButton);
      //.addExtraAction(exportActionButton);

    AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_NEW_ELEMENT);
    action.registerCustomShortcutSet(action.getShortcutSet(), myTree);
    JPanel panel = decorator.createPanel();

    panel.setBorder(JBUI.Borders.empty());
    add(panel, BorderLayout.CENTER);
    setBorder(JBUI.Borders.empty());
    myAutoScrollToSourceHandler = new AutoScrollToSourceHandler() {
      @Override
      protected boolean isAutoScrollMode() {
        return favoriteManager.getViewSettings().isAutoScrollToSource();
      }

      @Override
      protected void setAutoScrollMode(boolean state) {
        favoriteManager.getViewSettings().setAutoScrollToSource(state);
      }
    };
    myAutoScrollToSourceHandler.install(myTree);
    favoriteManager.addFavoritesListener(new FavoritesListener() {
      @Override
      public void rootsChanged() {
        doUpdate();
      }

      @Override
      public void listAdded(@NotNull String listName) {
        doUpdate();
      }

      @Override
      public void listRemoved(@NotNull String listName) {
        doUpdate();
      }
    }, project);
    FavoriteNodeProvider.EP_NAME.getPoint(myProject).addChangeListener(this::doUpdate, myProject);
  }

  private void doUpdate() {
    myBuilder.updateFromRoot();
    myTree.repaint();
  }

  public void selectElement(final Object selector, final VirtualFile file, final boolean requestFocus) {
    myBuilder.selectAsync(selector, file, requestFocus);
  }

  public DnDAwareTree getTree() {
    return myTree;
  }

  private PsiElement @NotNull [] getSelectedPsiElements() {
    List<PsiElement> elements = JBIterable.of(getSelectedNodeElements()).filterMap(this::getPsiElement).toList();
    return PsiUtilCore.toPsiElementArray(elements);
  }

  @Nullable
  private PsiElement getPsiElement(@Nullable Object element) {
    if (element instanceof FavoriteTreeNodeDescriptor) {
      element = ((FavoriteTreeNodeDescriptor)element).getElement().getValue();
    }
    if (element instanceof Bookmark) {
      element = ((Bookmark)element).getFile();
    }
    if (element instanceof PsiElement) {
      return (PsiElement)element;
    }
    else if (element instanceof SmartPsiElementPointer) {
      return ((SmartPsiElementPointer)element).getElement();
    }
    else if (element != null) {
      for (FavoriteNodeProvider provider : FavoriteNodeProvider.EP_NAME.getExtensions(myProject)) {
        PsiElement psiElement = provider.getPsiElement(element);
        if (psiElement != null) {
          return psiElement;
        }
      }
    }
    return null;
  }

  @Override
  public Object getData(@NotNull String dataId) {
    if (CommonDataKeys.PROJECT.is(dataId)) {
      return myProject;
    }
    if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
      final FavoriteTreeNodeDescriptor[] selectedNodeDescriptors = FavoritesTreeUtil.getSelectedNodeDescriptors(myTree);
      return selectedNodeDescriptors.length == 1 ? selectedNodeDescriptors[0].getElement() : null;
    }
    FavoritesManager favoriteManager = FavoritesManager.getInstance(myProject);
    if (CommonDataKeys.NAVIGATABLE_ARRAY.is(dataId)) {
      List<String> listNames = getSelectedElements(String.class);
      final List<Navigatable> selectedElements = new SmartList<>();
      for (String listname : listNames) {
        selectedElements.addAll(ContainerUtil.map(favoriteManager.getVirtualFiles(listname, false), file -> new OpenFileDescriptor(myProject, file)));
      }
      selectedElements.addAll(getSelectedElements(Navigatable.class));
      return selectedElements.isEmpty() ? null : selectedElements.toArray(new Navigatable[0]);
    }

    if (PlatformDataKeys.CUT_PROVIDER.is(dataId)) {
      return myCopyPasteDelegator.getCutProvider();
    }
    if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
      return myCopyPasteDelegator.getCopyProvider();
    }
    if (PlatformDataKeys.PASTE_PROVIDER.is(dataId)) {
      return myCopyPasteDelegator.getPasteProvider();
    }
    if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return "reference.toolWindows.favorites";
    }
    if (LangDataKeys.NO_NEW_ACTION.is(dataId)) {
      return Boolean.TRUE;
    }
    if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
      PsiElement[] elements = getSelectedPsiElements();
      if (elements.length != 1) {
        return null;
      }
      return elements[0] != null && elements[0].isValid() ? elements[0] : null;
    }
    if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
      final PsiElement[] elements = getSelectedPsiElements();
      ArrayList<PsiElement> result = new ArrayList<>();
      for (PsiElement element : elements) {
        if (element.isValid()) {
          result.add(element);
        }
      }
      return result.isEmpty() ? null : PsiUtilCore.toPsiElementArray(result);
    }

    if (LangDataKeys.IDE_VIEW.is(dataId)) {
      return myIdeView;
    }

    if (LangDataKeys.TARGET_PSI_ELEMENT.is(dataId)) {
      return null;
    }

    if (LangDataKeys.MODULE_CONTEXT.is(dataId)) {
      Module[] selected = getSelectedModules();
      return selected != null && selected.length == 1 ? selected[0] : null;
    }
    if (LangDataKeys.MODULE_CONTEXT_ARRAY.is(dataId)) {
      return getSelectedModules();
    }
    if (ModuleGroup.ARRAY_DATA_KEY.is(dataId)) {
      final List<ModuleGroup> selectedElements = getSelectedElements(ModuleGroup.class);
      return selectedElements.isEmpty() ? null : selectedElements.toArray(new ModuleGroup[0]);
    }
    if (LibraryGroupElement.ARRAY_DATA_KEY.is(dataId)) {
      final List<LibraryGroupElement> selectedElements = getSelectedElements(LibraryGroupElement.class);
      return selectedElements.isEmpty() ? null : selectedElements.toArray(new LibraryGroupElement[0]);
    }
    if (NamedLibraryElement.ARRAY_DATA_KEY.is(dataId)) {
      final List<NamedLibraryElement> selectedElements = getSelectedElements(NamedLibraryElement.class);
      return selectedElements.isEmpty() ? null : selectedElements.toArray(new NamedLibraryElement[0]);
    }
    if (CONTEXT_FAVORITES_ROOTS_DATA_KEY.is(dataId)) {
      List<FavoriteTreeNodeDescriptor> result = new ArrayList<>();
      FavoriteTreeNodeDescriptor[] selectedNodeDescriptors = FavoritesTreeUtil.getSelectedNodeDescriptors(myTree);
      for (FavoriteTreeNodeDescriptor selectedNodeDescriptor : selectedNodeDescriptors) {
        if (FavoritesTreeUtil.getProvider(favoriteManager, selectedNodeDescriptor) != null) {
          continue;
        }
        FavoriteTreeNodeDescriptor root = selectedNodeDescriptor.getFavoritesRoot();
        if (root != null && root.getElement() instanceof FavoritesListNode) {
          result.add(selectedNodeDescriptor);
        }
      }
      return result.toArray(FavoriteTreeNodeDescriptor.EMPTY_ARRAY);
    }
    if (FAVORITES_TREE_KEY.is(dataId)) {
      return myTree;
    }
    if (FAVORITES_TREE_BUILDER_KEY.is(dataId)) {
      return myBuilder;
    }
    if (FAVORITES_LIST_NAME_DATA_KEY.is(dataId)) {
      final FavoriteTreeNodeDescriptor[] descriptors = FavoritesTreeUtil.getSelectedNodeDescriptors(myTree);
      Set<String> selectedNames = new HashSet<>();
      for (FavoriteTreeNodeDescriptor descriptor : descriptors) {
        FavoritesListNode node = FavoritesTreeUtil.extractParentList(descriptor);
        if (node != null) {
          selectedNames.add(node.getValue());
        }
      }

      if (selectedNames.size() == 1) {
        return selectedNames.iterator().next();
      }
      return null;
    }
    FavoriteTreeNodeDescriptor[] descriptors = FavoritesTreeUtil.getSelectedNodeDescriptors(myTree);
    if (descriptors.length > 0) {
      List<AbstractTreeNode<?>> nodes = new ArrayList<>();
      for (FavoriteTreeNodeDescriptor descriptor : descriptors) {
        nodes.add(descriptor.getElement());
      }
      return myFavoritesTreeStructure.getDataFromProviders(nodes, dataId);
    }
    return null;
  }

  private <T> List<T> getSelectedElements(Class<T> klass) {
    final Object[] elements = getSelectedNodeElements();
    ArrayList<T> result = new ArrayList<>();
    for (Object element : elements) {
      if (element == null) continue;
      if (klass.isAssignableFrom(element.getClass())) {
        result.add((T)element);
      }
    }
    return result;
  }

  private Module[] getSelectedModules() {
    final Object[] elements = getSelectedNodeElements();
    ArrayList<Module> result = new ArrayList<>();
    for (Object element : elements) {
      if (element instanceof Module) {
        result.add((Module)element);
      }
      else if (element instanceof ModuleGroup) {
        result.addAll(((ModuleGroup)element).modulesInGroup(myProject, true));
      }
    }

    return result.isEmpty() ? null : result.toArray(Module.EMPTY_ARRAY);
  }

  private Object @NotNull [] getSelectedNodeElements() {
    FavoriteTreeNodeDescriptor[] selectedNodeDescriptors = FavoritesTreeUtil.getSelectedNodeDescriptors(myTree);
    List<Object> result = new ArrayList<>();
    for (FavoriteTreeNodeDescriptor selectedNodeDescriptor : selectedNodeDescriptors) {
      if (selectedNodeDescriptor != null) {
        Object value = selectedNodeDescriptor.getElement().getValue();
        if (value instanceof SmartPsiElementPointer) {
          value = ((SmartPsiElementPointer)value).getElement();
        }
        result.add(value);
      }
    }
    return ArrayUtil.toObjectArray(result);
  }

  public void setupToolWindow(@NotNull ToolWindowEx window) {
    final CollapseAllAction collapseAction = new CollapseAllAction(myTree);
    collapseAction.getTemplatePresentation().setIcon(AllIcons.Actions.Collapseall);
    window.setTitleActions(collapseAction);

    final DefaultActionGroup group = new DefaultActionGroup();
    final ProjectViewDirectoryHelper helper = ProjectViewDirectoryHelper.getInstance(myProject);

    if (helper.supportsFlattenPackages()) {
      group.add(new FavoritesFlattenPackagesAction(myProject, myBuilder));
    }
    if (helper.supportsHideEmptyMiddlePackages()) {
      group.add(new FavoritesCompactEmptyMiddlePackagesAction(myProject, myBuilder));
    }
    if (helper.supportsFlattenPackages()) {
      group.addAction(new FavoritesAbbreviatePackageNamesAction(myProject, myBuilder));
    }
    //todo move this logic to ProjectViewDirectoryHelper.supportsShowMembers
    if (!PlatformUtils.isCidr() && !PlatformUtils.isRider()) {
      group.add(new FavoritesShowMembersAction(myProject, myBuilder));
    }

    final FavoritesAutoscrollFromSourceHandler handler = new FavoritesAutoscrollFromSourceHandler(myProject, myBuilder);
    handler.install();
    group.add(handler.createToggleAction());

    group.add(new FavoritesAutoScrollToSourceAction(myProject, myAutoScrollToSourceHandler, myBuilder));
    window.setAdditionalGearActions(group);
  }

  public static String getQualifiedName(final VirtualFile file) {
    return file.getPresentableUrl();
  }

  public FavoritesViewTreeBuilder getBuilder() {
    return myBuilder;
  }

  @Nullable
  FavoritesListNode findFavoritesListNode(Point point) {
    final TreePath path = myTree.getClosestPathForLocation(point.x, point.y);
    final FavoritesListNode node = getListNodeFromPath(path);
    return node == null ? (FavoritesListNode)((FavoritesRootNode)myFavoritesTreeStructure.getRootElement()).getChildren().iterator().next()
                        : node;
  }

  static FavoritesListNode getListNodeFromPath(TreePath path) {
    if (path != null && path.getPathCount() > 1) {
      final Object o = path.getPath()[1];
      if (o instanceof DefaultMutableTreeNode) {
        final Object obj = ((DefaultMutableTreeNode)o).getUserObject();
        if (obj instanceof FavoriteTreeNodeDescriptor) {
          final AbstractTreeNode node = ((FavoriteTreeNodeDescriptor)obj).getElement();
          if (node instanceof FavoritesListNode) {
            return (FavoritesListNode)node;
          }
        }
      }
    }
    return null;
  }

  void dropPsiElements(FavoritesManager mgr, FavoritesListNode node, PsiElement[] elements) {
    if (elements != null && elements.length > 0) {
      final ArrayList<AbstractTreeNode<?>> nodes = new ArrayList<>();
      for (PsiElement element : elements) {
        if (element instanceof SmartPsiElementPointer) {
          element = ((SmartPsiElementPointer)element).getElement();
        }
        final Collection<AbstractTreeNode<?>> tmp = AddToFavoritesAction
          .createNodes(myProject, null, element, true, FavoritesManager.getInstance(myProject).getViewSettings());
        nodes.addAll(tmp);
        mgr.addRoots(node.getValue(), nodes);
      }
      myBuilder.select(nodes.toArray(), null);
    }
  }

  private final class MyIdeView implements IdeView {
    @Override
    public void selectElement(final PsiElement element) {
      if (element != null) {
        selectPsiElement(element, false);
        boolean requestFocus = true;
        final boolean isDirectory = element instanceof PsiDirectory;
        if (!isDirectory) {
          Editor editor = EditorHelper.openInEditor(element);
          if (editor != null) {
            ToolWindowManager.getInstance(myProject).activateEditorComponent();
            requestFocus = false;
          }
        }
        if (requestFocus) {
          selectPsiElement(element, true);
        }
      }
    }

    private void selectPsiElement(PsiElement element, boolean requestFocus) {
      VirtualFile virtualFile = PsiUtilCore.getVirtualFile(element);
      FavoritesTreeViewPanel.this.selectElement(element, virtualFile, requestFocus);
    }

    private PsiDirectory @Nullable [] getSelectedDirectories() {
      if (myBuilder == null) return null;
      final Object[] selectedNodeElements = getSelectedNodeElements();
      if (selectedNodeElements.length != 1) return null;
      for (FavoriteNodeProvider nodeProvider : FavoriteNodeProvider.EP_NAME.getExtensions(myProject)) {
        final PsiElement psiElement = nodeProvider.getPsiElement(selectedNodeElements[0]);
        if (psiElement instanceof PsiDirectory) {
          return new PsiDirectory[]{(PsiDirectory)psiElement};
        }
        else if (psiElement instanceof PsiDirectoryContainer) {
          final String moduleName = nodeProvider.getElementModuleName(selectedNodeElements[0]);
          GlobalSearchScope searchScope = GlobalSearchScope.projectScope(myProject);
          if (moduleName != null) {
            final Module module = ModuleManager.getInstance(myProject).findModuleByName(moduleName);
            if (module != null) {
              searchScope = GlobalSearchScope.moduleScope(module);
            }
          }
          return ((PsiDirectoryContainer)psiElement).getDirectories(searchScope);
        }
        else if (psiElement != null) {
          PsiFile file = psiElement.getContainingFile();
          if (file != null) {
            PsiDirectory parent = file.getParent();
            if (parent != null) {
              return new PsiDirectory[]{parent};
            }
          }
        }
      }
      return selectedNodeElements[0] instanceof PsiDirectory ? new PsiDirectory[]{(PsiDirectory)selectedNodeElements[0]} : null;
    }

    @Override
    public PsiDirectory @NotNull [] getDirectories() {
      final PsiDirectory[] directories = getSelectedDirectories();
      return directories == null ? PsiDirectory.EMPTY_ARRAY : directories;
    }

    @Override
    public PsiDirectory getOrChooseDirectory() {
      return DirectoryChooserUtil.getOrChooseDirectory(this);
    }
  }

  @NotNull
  @Override
  public RelativeRectangle getAcceptArea() {
    return new RelativeRectangle(myTree);
  }

  @NotNull
  @Override
  public ContentResponse getContentResponse(@NotNull DockableContent content, RelativePoint point) {
    if (content.getKey() instanceof VirtualFile) {
      return ContentResponse.ACCEPT_COPY;
    }
    return ContentResponse.DENY;
  }

  @Override
  public JComponent getContainerComponent() {
    return this;
  }

  @Override
  public void add(@NotNull DockableContent content, RelativePoint dropTarget) {
    if (!(content.getKey() instanceof VirtualFile)) {
      return;
    }

    VirtualFile vFile = (VirtualFile)content.getKey();
    PsiFileSystemItem psiFile = PsiUtilCore.findFileSystemItem(myProject, vFile);
    Point p = dropTarget.getScreenPoint();
    SwingUtilities.convertPointFromScreen(p, myTree);
    FavoritesListNode node = findFavoritesListNode(p);
    if (node != null && psiFile != null) {
      dropPsiElements(FavoritesManager.getInstance(myProject), node, new PsiElement[]{psiFile});
    }
  }

  @Override
  public boolean isEmpty() {
    return myTree.isEmpty();
  }

  @Nullable
  @Override
  public Image processDropOver(@NotNull DockableContent content, RelativePoint point) {
    Point p = point.getScreenPoint();
    SwingUtilities.convertPointFromScreen(p, myTree);
    TreePath treePath = myTree.getClosestPathForLocation(p.x, p.y);
    FavoritesListNode node = getListNodeFromPath(treePath);
    treePath = node != null ? myTree.getPath(node) : null;
    if (treePath != null) {
      myTree.setSelectionPath(treePath);
    }
    return null;
  }

  @Override
  public boolean isDisposeWhenEmpty() {
    return false;
  }
}
