// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.projectView.impl;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.impl.PackagesPaneSelectInTarget;
import com.intellij.ide.projectView.BaseProjectTreeBuilder;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.ide.projectView.impl.nodes.PackageElementNode;
import com.intellij.ide.projectView.impl.nodes.PackageUtil;
import com.intellij.ide.projectView.impl.nodes.PackageViewProjectNode;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.util.*;

public class PackageViewPane extends AbstractProjectViewPSIPane {
  @NonNls public static final String ID = "PackagesPane";
  private final MyDeletePSIElementProvider myDeletePSIElementProvider = new MyDeletePSIElementProvider();

  public PackageViewPane(Project project) {
    super(project);
  }

  @NotNull
  @Override
  public String getTitle() {
    return IdeBundle.message("title.packages");
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.CopyOfFolder;
  }

  @Override
  @NotNull
  public String getId() {
    return ID;
  }

  @NotNull
  @Override
  public List<PsiElement> getElementsFromNode(@Nullable Object node) {
    Object o = getValueFromNode(node);
    if (o instanceof PackageElement) {
      PsiPackage aPackage = ((PackageElement)o).getPackage();
      return ContainerUtil.createMaybeSingletonList(aPackage.isValid() ? aPackage : null);
    }
    return super.getElementsFromNode(node);
  }

  @Override
  protected Module getNodeModule(@Nullable Object element) {
    if (element instanceof PackageElement) {
      return ((PackageElement)element).getModule();
    }
    return super.getNodeModule(element);
  }

  @Override
  public Object getData(@NotNull final String dataId) {
    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
      final PackageElement selectedPackageElement = getSelectedPackageElement();
      if (selectedPackageElement != null) {
        return myDeletePSIElementProvider;
      }
    }
    if (PackageElement.DATA_KEY.is(dataId)) {
      final PackageElement packageElement = getSelectedPackageElement();
    }
    if (LangDataKeys.MODULE.is(dataId)) {
      final PackageElement packageElement = getSelectedPackageElement();
      if (packageElement != null) {
        return packageElement.getModule();
      }
    }
    return super.getData(dataId);
  }

  @Nullable
  private PackageElement getSelectedPackageElement() {
    AbstractTreeNode node = TreeUtil.getLastUserObject(AbstractTreeNode.class, getSelectedPath());
    Object selected = node == null ? null : node.getValue();
    return selected instanceof PackageElement ? (PackageElement)selected : null;
  }

  @NotNull
  @Override
  public PsiDirectory[] getSelectedDirectories() {
    List<PsiDirectory> directories = ContainerUtil.newArrayList();
    for (PackageElementNode node : getSelectedNodes(PackageElementNode.class)) {
      PackageElement packageElement = node.getValue();
      PsiPackage aPackage = packageElement != null ? packageElement.getPackage() : null;
      final Module module = packageElement != null ? packageElement.getModule() : null;
      if (aPackage != null && module != null) {
        GlobalSearchScope scope = GlobalSearchScope.moduleScope(module);
        Collections.addAll(directories, aPackage.getDirectories(scope));

        if (Registry.is("projectView.choose.directory.on.compacted.middle.packages")) {
          Object parentValue = node.getParent().getValue();
          PsiPackage parentNodePackage = parentValue instanceof PackageElement ? ((PackageElement)parentValue).getPackage() : null;
          while (true) {
            aPackage = aPackage.getParentPackage();
            if (aPackage == null || aPackage.getQualifiedName().isEmpty() || aPackage.equals(parentNodePackage)) {
              break;
            }
            Collections.addAll(directories, aPackage.getDirectories(scope));
          }
        }
      }
    }
    if (!directories.isEmpty()) {
      return directories.toArray(PsiDirectory.EMPTY_ARRAY);
    }

    return super.getSelectedDirectories();
  }

  private final class ShowLibraryContentsAction extends ToggleAction {
    private ShowLibraryContentsAction() {
      super(IdeBundle.message("action.show.libraries.contents"), IdeBundle.message("action.show.hide.library.contents"),
            AllIcons.ObjectBrowser.ShowLibraryContents);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent event) {
      return ProjectView.getInstance(myProject).isShowLibraryContents(getId());
    }

    @Override
    public void setSelected(@NotNull AnActionEvent event, boolean flag) {
      final ProjectViewImpl projectView = (ProjectViewImpl)ProjectView.getInstance(myProject);
      projectView.setShowLibraryContents(getId(), flag);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      final ProjectViewImpl projectView = (ProjectViewImpl)ProjectView.getInstance(myProject);
      presentation.setVisible(projectView.getCurrentProjectViewPane() == PackageViewPane.this);
    }
  }

  @Override
  public void addToolbarActions(@NotNull DefaultActionGroup actionGroup) {
    actionGroup.addAction(new ShowModulesAction(myProject, ID)).setAsSecondary(true);
    actionGroup.addAction(createFlattenModulesAction(() -> true)).setAsSecondary(true);
    actionGroup.addAction(new ShowLibraryContentsAction()).setAsSecondary(true);
    AnAction editScopesAction = ActionManager.getInstance().getAction("ScopeView.EditScopes");
    if (editScopesAction != null) actionGroup.addAction(editScopesAction).setAsSecondary(true);
  }

  @NotNull
  @Override
  protected AbstractTreeUpdater createTreeUpdater(@NotNull AbstractTreeBuilder treeBuilder) {
    return new PackageViewTreeUpdater(treeBuilder);
  }

  @NotNull
  @Override
  public SelectInTarget createSelectInTarget() {
    return new PackagesPaneSelectInTarget(myProject);
  }

  @NotNull
  @Override
  protected ProjectAbstractTreeStructureBase createStructure() {
    return new ProjectTreeStructure(myProject, ID){
      @Override
      protected AbstractTreeNode createRoot(@NotNull final Project project, @NotNull ViewSettings settings) {
        return new PackageViewProjectNode(project, settings);
      }

      @Override
      public boolean isToBuildChildrenInBackground(@NotNull Object element) {
        return Registry.is("ide.projectView.PackageViewTreeStructure.BuildChildrenInBackground");
      }
    };
  }

  @NotNull
  @Override
  protected ProjectViewTree createTree(@NotNull DefaultTreeModel treeModel) {
    return new ProjectViewTree(treeModel) {
      public String toString() {
        return getTitle() + " " + super.toString();
      }
    };
  }

  @NotNull
  public String getComponentName() {
    return "PackagesPane";
  }

  @Override
  public int getWeight() {
    return 1;
  }

  private final class PackageViewTreeUpdater extends AbstractTreeUpdater {
    private PackageViewTreeUpdater(final AbstractTreeBuilder treeBuilder) {
      super(treeBuilder);
    }

    @Override
    public boolean addSubtreeToUpdateByElement(@NotNull Object element) {
      // should convert PsiDirectories into PackageElements
      if (element instanceof PsiDirectory) {
        PsiDirectory dir = (PsiDirectory)element;
        final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(dir);
        if (ProjectView.getInstance(myProject).isShowModules(getId())) {
          Module[] modules = getModulesFor(dir);
          boolean rv = false;
          for (Module module : modules) {
            rv |= addPackageElementToUpdate(aPackage, module);
          }
          return rv;
        }
        else {
          return addPackageElementToUpdate(aPackage, null);
        }
      }

      return super.addSubtreeToUpdateByElement(element);
    }

    private boolean addPackageElementToUpdate(final PsiPackage aPackage, Module module) {
      final ProjectTreeStructure packageTreeStructure = (ProjectTreeStructure)myTreeStructure;
      PsiPackage packageToUpdateFrom = aPackage;
      if (!packageTreeStructure.isFlattenPackages() && packageTreeStructure.isHideEmptyMiddlePackages()) {
        // optimization: this check makes sense only if flattenPackages == false && HideEmptyMiddle == true
        while (packageToUpdateFrom != null && packageToUpdateFrom.isValid() && PackageUtil.isPackageEmpty(packageToUpdateFrom, module, true, false)) {
          packageToUpdateFrom = packageToUpdateFrom.getParentPackage();
        }
      }
      boolean addedOk;
      while (!(addedOk = super.addSubtreeToUpdateByElement(getTreeElementToUpdateFrom(packageToUpdateFrom, module)))) {
        if (packageToUpdateFrom == null) {
          break;
        }
        packageToUpdateFrom = packageToUpdateFrom.getParentPackage();
      }
      return addedOk;
    }

    @NotNull
    private Object getTreeElementToUpdateFrom(PsiPackage packageToUpdateFrom, Module module) {
      if (packageToUpdateFrom == null || !packageToUpdateFrom.isValid() || "".equals(packageToUpdateFrom.getQualifiedName())) {
        return module == null ? myTreeStructure.getRootElement() : module;
      }
      else {
        return new PackageElement(module, packageToUpdateFrom, false);
      }
    }

    private Module[] getModulesFor(PsiDirectory dir) {
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
      final VirtualFile vFile = dir.getVirtualFile();
      final Set<Module> modules = new HashSet<>();
      final Module module = fileIndex.getModuleForFile(vFile);
      if (module != null) {
        modules.add(module);
      }
      if (fileIndex.isInLibrary(vFile)) {
        final List<OrderEntry> orderEntries = fileIndex.getOrderEntriesForFile(vFile);
        if (orderEntries.isEmpty()) {
          return Module.EMPTY_ARRAY;
        }
        for (OrderEntry entry : orderEntries) {
          modules.add(entry.getOwnerModule());
        }
      }
      return modules.toArray(Module.EMPTY_ARRAY);
    }
  }

  private final class MyDeletePSIElementProvider implements DeleteProvider {
    @Override
    public boolean canDeleteElement(@NotNull DataContext dataContext) {
      for (PsiDirectory directory : getSelectedDirectories()) {
        if (!directory.getManager().isInProject(directory)) return false;
      }
      return true;
    }

    @Override
    public void deleteElement(@NotNull DataContext dataContext) {
      PsiDirectory[] allElements = getSelectedDirectories();
      List<PsiElement> validElements = new ArrayList<>();
      for (PsiElement psiElement : allElements) {
        if (psiElement != null && psiElement.isValid()) validElements.add(psiElement);
      }
      final PsiElement[] elements = PsiUtilCore.toPsiElementArray(validElements);

      LocalHistoryAction a = LocalHistory.getInstance().startAction(IdeBundle.message("progress.deleting"));
      try {
        DeleteHandler.deletePsiElement(elements, myProject);
      }
      finally {
        a.finish();
      }
    }
  }

  @Override
  protected BaseProjectTreeBuilder createBuilder(@NotNull DefaultTreeModel model) {
    return null;
  }
}
