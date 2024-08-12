// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.favoritesTreeView.actions;

import com.intellij.ide.favoritesTreeView.FavoriteNodeProvider;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewSettings;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.nodes.*;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * @deprecated Stub left for plugin API compatibility
 */
@Deprecated(forRemoval = true)
public class AddToFavoritesAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(AddToFavoritesAction.class);

  public AddToFavoritesAction(String choosenList) {
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
  }

  public static @NotNull Collection<AbstractTreeNode<?>> getNodesToAdd(final DataContext dataContext, final boolean inProjectView) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);

    if (project == null) return Collections.emptyList();

    Module moduleContext = LangDataKeys.MODULE_CONTEXT.getData(dataContext);

    Collection<AbstractTreeNode<?>> nodesToAdd = null;
    for (FavoriteNodeProvider provider : FavoriteNodeProvider.EP_NAME.getExtensions(project)) {
      Collection<AbstractTreeNode<?>> nodes = provider.getFavoriteNodes(dataContext, ProjectViewSettings.Immutable.DEFAULT);
      if (nodes != null && !nodes.isEmpty()) {
        nodesToAdd = nodes;
        break;
      }
    }

    if (nodesToAdd == null) {
      Object elements = collectSelectedElements(dataContext);
      if (elements != null) {
        nodesToAdd = createNodes(project, moduleContext, elements, inProjectView, ProjectViewSettings.Immutable.DEFAULT);
      }
    }
    return nodesToAdd == null ? Collections.emptyList() : nodesToAdd;
  }


  static Object retrieveData(Object object, Object data) {
    return object == null ? data : object;
  }

  private static Object collectSelectedElements(final DataContext dataContext) {
    Object elements = retrieveData(null, CommonDataKeys.PSI_ELEMENT.getData(dataContext));
    elements = retrieveData(elements, LangDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext));
    elements = retrieveData(elements, CommonDataKeys.PSI_FILE.getData(dataContext));
    elements = retrieveData(elements, ModuleGroup.ARRAY_DATA_KEY.getData(dataContext));
    elements = retrieveData(elements, LangDataKeys.MODULE_CONTEXT_ARRAY.getData(dataContext));
    elements = retrieveData(elements, LibraryGroupElement.ARRAY_DATA_KEY.getData(dataContext));
    elements = retrieveData(elements, NamedLibraryElement.ARRAY_DATA_KEY.getData(dataContext));
    elements = retrieveData(elements, CommonDataKeys.VIRTUAL_FILE.getData(dataContext));
    elements = retrieveData(elements, CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext));
    return elements;
  }

  public static
  @NotNull
  Collection<AbstractTreeNode<?>> createNodes(Project project,
                                           Module moduleContext,
                                           Object object,
                                           boolean inProjectView,
                                           @NotNull ViewSettings favoritesConfig) {
    if (project == null) return Collections.emptyList();
    ArrayList<AbstractTreeNode<?>> result = new ArrayList<>();
    for (FavoriteNodeProvider provider : FavoriteNodeProvider.EP_NAME.getExtensions(project)) {
      final AbstractTreeNode treeNode = provider.createNode(project, object, favoritesConfig);
      if (treeNode != null) {
        result.add(treeNode);
        return result;
      }
    }
    final PsiManager psiManager = PsiManager.getInstance(project);

    final String currentViewId = ProjectView.getInstance(project).getCurrentViewId();
    AbstractProjectViewPane pane = ProjectView.getInstance(project).getProjectViewPaneById(currentViewId);


    //on psi elements
    if (object instanceof PsiElement[]) {
      for (PsiElement psiElement : (PsiElement[])object) {
        addPsiElementNode(psiElement, project, result, favoritesConfig);
      }
      return result;
    }

    //on psi element
    if (object instanceof PsiElement) {
      Module containingModule = null;
      if (inProjectView && ProjectView.getInstance(project).isShowModules(currentViewId)) {
        if (pane != null && pane.getSelectedDescriptor() != null &&
            pane.getSelectedDescriptor().getElement() instanceof AbstractTreeNode abstractTreeNode) {
          while (abstractTreeNode != null && !(abstractTreeNode.getParent() instanceof AbstractModuleNode)) {
            abstractTreeNode = abstractTreeNode.getParent();
          }
          if (abstractTreeNode != null) {
            containingModule = ((AbstractModuleNode)abstractTreeNode.getParent()).getValue();
          }
        }
      }
      addPsiElementNode((PsiElement)object, project, result, favoritesConfig);
      return result;
    }

    if (object instanceof VirtualFile[]) {
      for (VirtualFile vFile : (VirtualFile[])object) {
        PsiElement element = psiManager.findFile(vFile);
        if (element == null) element = psiManager.findDirectory(vFile);
        addPsiElementNode(element,
                          project,
                          result,
                          favoritesConfig);
      }
      return result;
    }

    //on form in editor
    if (object instanceof VirtualFile vFile) {
      final PsiFile psiFile = psiManager.findFile(vFile);
      addPsiElementNode(psiFile, project, result, favoritesConfig);
      return result;
    }

    //on module groups
    if (object instanceof ModuleGroup[]) {
      for (ModuleGroup moduleGroup : (ModuleGroup[])object) {
        result.add(new ProjectViewModuleGroupNode(project, moduleGroup, favoritesConfig));
      }
      return result;
    }

    //on module nodes
    if (object instanceof Module) object = new Module[]{(Module)object};
    if (object instanceof Module[]) {
      for (Module module1 : (Module[])object) {
        result.add(new ProjectViewModuleNode(project, module1, favoritesConfig));
      }
      return result;
    }

    //on library group node
    if (object instanceof LibraryGroupElement[]) {
      for (LibraryGroupElement libraryGroup : (LibraryGroupElement[])object) {
        result.add(new LibraryGroupNode(project, libraryGroup, favoritesConfig));
      }
      return result;
    }

    //on named library node
    if (object instanceof NamedLibraryElement[]) {
      for (NamedLibraryElement namedLibrary : (NamedLibraryElement[])object) {
        result.add(new NamedLibraryElementNode(project, namedLibrary, favoritesConfig));
      }
      return result;
    }
    return result;
  }

  private static void addPsiElementNode(PsiElement psiElement,
                                        final Project project,
                                        final ArrayList<? super AbstractTreeNode<?>> result,
                                        @NotNull ViewSettings favoritesConfig) {

    Class<? extends AbstractTreeNode<?>> klass = getPsiElementNodeClass(psiElement);
    if (klass == null) {
      psiElement = PsiTreeUtil.getParentOfType(psiElement, PsiFile.class);
      if (psiElement != null) {
        klass = PsiFileNode.class;
      }
    }

    final Object value = psiElement;
    try {
      if (klass != null && value != null) {
        result.add(ProjectViewNode.createTreeNode(klass, project, value, favoritesConfig));
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }


  private static Class<? extends AbstractTreeNode<?>> getPsiElementNodeClass(PsiElement psiElement) {
    Class<? extends AbstractTreeNode<?>> klass = null;
    if (psiElement instanceof PsiFile) {
      klass = PsiFileNode.class;
    }
    else if (psiElement instanceof PsiDirectory) {
      klass = PsiDirectoryNode.class;
    }
    return klass;
  }
}