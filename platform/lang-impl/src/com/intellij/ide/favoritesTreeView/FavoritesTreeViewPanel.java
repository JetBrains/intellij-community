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

package com.intellij.ide.favoritesTreeView;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.CopyPasteDelegator;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.nodes.LibraryGroupElement;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement;
import com.intellij.ide.ui.customization.CustomizationUtil;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.ide.util.EditorHelper;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ArrayUtil;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FavoritesTreeViewPanel extends JPanel implements DataProvider {
  @NonNls public static final String ABSTRACT_TREE_NODE_TRANSFERABLE = "AbstractTransferable";

  private final FavoritesTreeStructure myFavoritesTreeStructure;
  private FavoritesViewTreeBuilder myBuilder;
  private final CopyPasteDelegator myCopyPasteDelegator;
  private final MouseListener myTreePopupHandler;

  public static final DataKey<FavoritesTreeNodeDescriptor[]> CONTEXT_FAVORITES_ROOTS_DATA_KEY = DataKey.create("FavoritesRoot");
  @Deprecated public static final String CONTEXT_FAVORITES_ROOTS = CONTEXT_FAVORITES_ROOTS_DATA_KEY.getName();

  public static final DataKey<String> FAVORITES_LIST_NAME_DATA_KEY = DataKey.create("FavoritesListName");
  @Deprecated public static final String FAVORITES_LIST_NAME = FAVORITES_LIST_NAME_DATA_KEY.getName();

  protected Project myProject;
  private final String myHelpId;
  protected DnDAwareTree myTree;

  private final MyDeletePSIElementProvider myDeletePSIElementProvider = new MyDeletePSIElementProvider();
  private final ModuleDeleteProvider myDeleteModuleProvider = new ModuleDeleteProvider();

  private String myListName;
  private final IdeView myIdeView = new MyIdeView();

  public FavoritesTreeViewPanel(Project project, String helpId, String name) {
    super(new BorderLayout());
    myProject = project;
    myHelpId = helpId;
    myListName = name;

    myFavoritesTreeStructure = new FavoritesTreeStructure(project, myListName);
    DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    root.setUserObject(myFavoritesTreeStructure.getRootElement());
    final DefaultTreeModel treeModel = new DefaultTreeModel(root);
    myTree = new DnDAwareTree(treeModel);
    myBuilder = new FavoritesViewTreeBuilder(myProject, myTree, treeModel, myFavoritesTreeStructure, myListName);

    TreeUtil.installActions(myTree);
    UIUtil.setLineStyleAngled(myTree);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.setLargeModel(true);
    new TreeSpeedSearch(myTree);
    ToolTipManager.sharedInstance().registerComponent(myTree);
    myTree.setCellRenderer(new NodeRenderer() {
      public void customizeCellRenderer(JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus);
        if (value instanceof DefaultMutableTreeNode) {
          final DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
          //only favorites roots to explain
          final Object userObject = node.getUserObject();
          if (userObject instanceof FavoritesTreeNodeDescriptor) {
            final FavoritesTreeNodeDescriptor favoritesTreeNodeDescriptor = (FavoritesTreeNodeDescriptor)userObject;
            AbstractTreeNode treeNode = favoritesTreeNodeDescriptor.getElement();
            final ItemPresentation presentation = treeNode.getPresentation();
            String locationString = presentation.getLocationString();
            if (locationString != null && locationString.length() > 0) {
              append(" (" + locationString + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
            }
            else if (node.getParent() != null && node.getParent().getParent() == null) {
              final String location = favoritesTreeNodeDescriptor.getLocation();
              if (location != null && location.length() > 0) {
                append(" (" + location + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
              }
            }
          }
        }
      }
    });
    JBScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTree);
    myTreePopupHandler = CustomizationUtil.installPopupHandler(myTree, IdeActions.GROUP_FAVORITES_VIEW_POPUP, ActionPlaces.FAVORITES_VIEW_POPUP);
    add(scrollPane, BorderLayout.CENTER);
    //add(createActionsToolbar(), BorderLayout.NORTH);

    EditSourceOnDoubleClickHandler.install(myTree);
    EditSourceOnEnterKeyHandler.install(myTree);
    myCopyPasteDelegator = new CopyPasteDelegator(myProject, this) {
      @NotNull
      protected PsiElement[] getSelectedElements() {
        return getSelectedPsiElements();
      }
    };
  }

  public void selectElement(final Object selector, final VirtualFile file, final boolean requestFocus) {
    myBuilder.select(selector, file, requestFocus);
  }

  public void dispose() {
    Disposer.dispose(myBuilder);
    myBuilder = null;
  }

  public DnDAwareTree getTree() {
    return myTree;
  }

  public String getName() {
    return myListName;
  }

  public void setName(String name) {
    myListName = name;
  }

  @NotNull
  private PsiElement[] getSelectedPsiElements() {
    final Object[] elements = getSelectedNodeElements();
    if (elements == null) {
      return PsiElement.EMPTY_ARRAY;
    }
    ArrayList<PsiElement> result = new ArrayList<PsiElement>();
    for (Object element : elements) {
      if (element instanceof PsiElement) {
        result.add((PsiElement)element);
      }
      else if (element instanceof SmartPsiElementPointer) {
        PsiElement psiElement = ((SmartPsiElementPointer)element).getElement();
        if (psiElement != null) {
          result.add(psiElement);
        }
      } else {
        for (FavoriteNodeProvider provider : Extensions.getExtensions(FavoriteNodeProvider.EP_NAME, myProject)) {
          final PsiElement psiElement = provider.getPsiElement(element);
          if (psiElement != null) {
            result.add(psiElement);
            break;
          }
        }
      }
    }
    return result.toArray(new PsiElement[result.size()]);
  }

  public FavoritesTreeStructure getFavoritesTreeStructure() {
    return myFavoritesTreeStructure;
  }

  public Object getData(String dataId) {
    if (PlatformDataKeys.PROJECT.is(dataId)) {
      return myProject;
    }
    if (PlatformDataKeys.NAVIGATABLE.is(dataId)) {
      final FavoritesTreeNodeDescriptor[] selectedNodeDescriptors = getSelectedNodeDescriptors();
      return selectedNodeDescriptors.length == 1 ? selectedNodeDescriptors[0].getElement() : null;
    }
    if (PlatformDataKeys.NAVIGATABLE_ARRAY.is(dataId)) {
      final List<Navigatable> selectedElements = getSelectedElements(Navigatable.class);
      return selectedElements.toArray(new Navigatable[selectedElements.size()]);
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
      return myHelpId;
    }
    if (LangDataKeys.PSI_ELEMENT.is(dataId)) {
      PsiElement[] elements = getSelectedPsiElements();
      if (elements.length != 1) {
        return null;
      }
      return elements[0] != null && elements[0].isValid() ? elements[0] : null;
    }
    if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
      final PsiElement[] elements = getSelectedPsiElements();
      ArrayList<PsiElement> result = new ArrayList<PsiElement>();
      for (PsiElement element : elements) {
        if (element.isValid()) {
          result.add(element);
        }
      }
      return result.isEmpty() ? null : result.toArray(new PsiElement[result.size()]);
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

    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
      final Object[] elements = getSelectedNodeElements();
      return elements != null && elements.length >= 1 && elements[0] instanceof Module
             ? myDeleteModuleProvider
             : myDeletePSIElementProvider;

    }
    if (ModuleGroup.ARRAY_DATA_KEY.is(dataId)) {
      final List<ModuleGroup> selectedElements = getSelectedElements(ModuleGroup.class);
      return selectedElements.isEmpty() ? null : selectedElements.toArray(new ModuleGroup[selectedElements.size()]);
    }
    if (LibraryGroupElement.ARRAY_DATA_KEY.is(dataId)) {
      final List<LibraryGroupElement> selectedElements = getSelectedElements(LibraryGroupElement.class);
      return selectedElements.isEmpty() ? null : selectedElements.toArray(new LibraryGroupElement[selectedElements.size()]);
    }
    if (NamedLibraryElement.ARRAY_DATA_KEY.is(dataId)) {
      final List<NamedLibraryElement> selectedElements = getSelectedElements(NamedLibraryElement.class);
      return selectedElements.isEmpty() ? null : selectedElements.toArray(new NamedLibraryElement[selectedElements.size()]);
    }
    if (CONTEXT_FAVORITES_ROOTS_DATA_KEY.is(dataId)) {
      List<FavoritesTreeNodeDescriptor> result = new ArrayList<FavoritesTreeNodeDescriptor>();
      FavoritesTreeNodeDescriptor[] selectedNodeDescriptors = getSelectedNodeDescriptors();
      for (FavoritesTreeNodeDescriptor selectedNodeDescriptor : selectedNodeDescriptors) {
        FavoritesTreeNodeDescriptor root = selectedNodeDescriptor.getFavoritesRoot();
        if (root != null && !(root.getElement().getValue() instanceof String)) {
          result.add(root);
        }
      }
      return result.toArray(new FavoritesTreeNodeDescriptor[result.size()]);
    }
    if (FAVORITES_LIST_NAME_DATA_KEY.is(dataId)) {
      return myListName;
    }
    FavoritesTreeNodeDescriptor[] descriptors = getSelectedNodeDescriptors();
    if (descriptors.length > 0) {
      List<AbstractTreeNode> nodes = new ArrayList<AbstractTreeNode>();
      for (FavoritesTreeNodeDescriptor descriptor : descriptors) {
        nodes.add(descriptor.getElement());
      }
      return myFavoritesTreeStructure.getDataFromProviders(nodes, dataId);
    }
    return null;
  }

  private <T> List<T> getSelectedElements(Class<T> klass) {
    final Object[] elements = getSelectedNodeElements();
    ArrayList<T> result = new ArrayList<T>();
    if (elements == null) {
      return result;
    }
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
    if (elements == null) {
      return null;
    }
    ArrayList<Module> result = new ArrayList<Module>();
    for (Object element : elements) {
      if (element instanceof Module) {
        result.add((Module)element);
      }
      else if (element instanceof ModuleGroup) {
        result.addAll(((ModuleGroup)element).modulesInGroup(myProject, true));
      }
    }

    return result.isEmpty() ? null : result.toArray(new Module[result.size()]);
  }

  private Object[] getSelectedNodeElements() {
    final FavoritesTreeNodeDescriptor[] selectedNodeDescriptors = getSelectedNodeDescriptors();
    ArrayList<Object> result = new ArrayList<Object>();
    for (FavoritesTreeNodeDescriptor selectedNodeDescriptor : selectedNodeDescriptors) {
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

  @NotNull
  public FavoritesTreeNodeDescriptor[] getSelectedNodeDescriptors() {
    TreePath[] path = myTree.getSelectionPaths();
    if (path == null) {
      return FavoritesTreeNodeDescriptor.EMPTY_ARRAY;
    }
    ArrayList<FavoritesTreeNodeDescriptor> result = new ArrayList<FavoritesTreeNodeDescriptor>();
    for (TreePath treePath : path) {
      DefaultMutableTreeNode lastPathNode = (DefaultMutableTreeNode)treePath.getLastPathComponent();
      Object userObject = lastPathNode.getUserObject();
      if (!(userObject instanceof FavoritesTreeNodeDescriptor)) {
        continue;
      }
      FavoritesTreeNodeDescriptor treeNodeDescriptor = (FavoritesTreeNodeDescriptor)userObject;
      result.add(treeNodeDescriptor);
    }
    return result.toArray(new FavoritesTreeNodeDescriptor[result.size()]);
  }

  public static String getQualifiedName(final VirtualFile file) {
    return file.getPresentableUrl();
  }

  public FavoritesViewTreeBuilder getBuilder() {
    return myBuilder;
  }

  private final class MyDeletePSIElementProvider implements DeleteProvider {
    public boolean canDeleteElement(DataContext dataContext) {
      final PsiElement[] elements = getElementsToDelete();
      return DeleteHandler.shouldEnableDeleteAction(elements);
    }

    public void deleteElement(DataContext dataContext) {
      List<PsiElement> allElements = Arrays.asList(getElementsToDelete());
      List<PsiElement> validElements = new ArrayList<PsiElement>();
      for (PsiElement psiElement : allElements) {
        if (psiElement != null && psiElement.isValid()) validElements.add(psiElement);
      }
      final PsiElement[] elements = validElements.toArray(new PsiElement[validElements.size()]);

      LocalHistoryAction a = LocalHistory.getInstance().startAction(IdeBundle.message("progress.deleting"));
      try {
        DeleteHandler.deletePsiElement(elements, myProject);
      }
      finally {
        a.finish();
      }
    }

    private PsiElement[] getElementsToDelete() {
      ArrayList<PsiElement> result = new ArrayList<PsiElement>();
      Object[] elements = getSelectedNodeElements();
      for (int idx = 0; elements != null && idx < elements.length; idx++) {
        if (elements[idx] instanceof PsiElement) {
          final PsiElement element = (PsiElement)elements[idx];
          result.add(element);
          if (element instanceof PsiDirectory) {
            final VirtualFile virtualFile = ((PsiDirectory)element).getVirtualFile();
            final String path = virtualFile.getPath();
            if (path.endsWith(JarFileSystem.JAR_SEPARATOR)) { // if is jar-file root
              final VirtualFile vFile =
                LocalFileSystem.getInstance().findFileByPath(path.substring(0, path.length() - JarFileSystem.JAR_SEPARATOR.length()));
              if (vFile != null) {
                final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(vFile);
                if (psiFile != null) {
                  elements[idx] = psiFile;
                }
              }
            }
          }
        }
      }

      return result.toArray(new PsiElement[result.size()]);
    }
  }

  private final class MyIdeView implements IdeView {
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
      VirtualFile virtualFile = PsiUtilBase.getVirtualFile(element);
      FavoritesTreeViewPanel.this.selectElement(element, virtualFile, requestFocus);
    }

    @Nullable
    private PsiDirectory[] getSelectedDirectories() {
      if (myBuilder == null) return null;
      final Object[] selectedNodeElements = getSelectedNodeElements();
      if (selectedNodeElements.length != 1) return null;
      for (FavoriteNodeProvider nodeProvider : Extensions.getExtensions(FavoriteNodeProvider.EP_NAME, myProject)) {
        final PsiElement psiElement = nodeProvider.getPsiElement(selectedNodeElements[0]);
        if (psiElement instanceof PsiDirectory) {
          return new PsiDirectory[]{(PsiDirectory)psiElement};
        } else if (psiElement instanceof PsiDirectoryContainer) {
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
      }
      return selectedNodeElements[0] instanceof PsiDirectory ? new PsiDirectory[] {(PsiDirectory)selectedNodeElements[0]} : null;
    }

    public PsiDirectory[] getDirectories() {
      final PsiDirectory[] directories = getSelectedDirectories();
      return directories == null ? PsiDirectory.EMPTY_ARRAY : directories;
    }

    public PsiDirectory getOrChooseDirectory() {
      return DirectoryChooserUtil.getOrChooseDirectory(this);
    }
  }
}
