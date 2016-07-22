/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.ide.projectView.impl;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.impl.PackagesPaneSelectInTarget;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.ide.projectView.impl.nodes.PackageElementNode;
import com.intellij.ide.projectView.impl.nodes.PackageUtil;
import com.intellij.ide.projectView.impl.nodes.PackageViewProjectNode;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.*;

public final class PackageViewPane extends AbstractProjectViewPSIPane {
  @NonNls public static final String ID = "PackagesPane";
  private final MyDeletePSIElementProvider myDeletePSIElementProvider = new MyDeletePSIElementProvider();

  public PackageViewPane(Project project) {
    super(project);
  }

  @Override
  public String getTitle() {
    return IdeBundle.message("title.packages");
  }

  @Override
  public Icon getIcon() {
    return AllIcons.General.PackagesTab;
  }

  @Override
  @NotNull
  public String getId() {
    return ID;
  }

  public AbstractTreeStructure getTreeStructure() {
    return myTreeStructure;
  }

  @Override
  protected PsiElement getPSIElement(@Nullable final Object element) {
    if (element instanceof PackageElement) {
      PsiPackage aPackage = ((PackageElement)element).getPackage();
      return aPackage != null && aPackage.isValid() ? aPackage : null;
    }
    return super.getPSIElement(element);
  }

  @Override
  protected Module getNodeModule(@Nullable Object element) {
    if (element instanceof PackageElement) {
      return ((PackageElement)element).getModule();
    }
    return super.getNodeModule(element);
  }

  @Override
  public Object getData(final String dataId) {
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
    PackageElement result = null;
    final DefaultMutableTreeNode selectedNode = getSelectedNode();
    if (selectedNode != null) {
      Object o = selectedNode.getUserObject();
      if (o instanceof AbstractTreeNode) {
        Object selected = ((AbstractTreeNode)o).getValue();
        result = selected instanceof PackageElement ? (PackageElement)selected : null;
      }
    }
    return result;
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
      return directories.toArray(new PsiDirectory[directories.size()]);
    }

    return super.getSelectedDirectories();
  }

  private final class ShowLibraryContentsAction extends ToggleAction {
    private ShowLibraryContentsAction() {
      super(IdeBundle.message("action.show.libraries.contents"), IdeBundle.message("action.show.hide.library.contents"),
            AllIcons.ObjectBrowser.ShowLibraryContents);
    }

    @Override
    public boolean isSelected(AnActionEvent event) {
      return ProjectView.getInstance(myProject).isShowLibraryContents(getId());
    }

    @Override
    public void setSelected(AnActionEvent event, boolean flag) {
      final ProjectViewImpl projectView = (ProjectViewImpl)ProjectView.getInstance(myProject);
      projectView.setShowLibraryContents(flag, getId());
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      final ProjectViewImpl projectView = (ProjectViewImpl)ProjectView.getInstance(myProject);
      presentation.setVisible(projectView.getCurrentProjectViewPane() == PackageViewPane.this);
    }
  }

  @Override
  public void addToolbarActions(DefaultActionGroup actionGroup) {
    actionGroup.addAction(new ShowModulesAction(myProject){
      @NotNull
      @Override
      protected String getId() {
        return PackageViewPane.this.getId();
      }
    }).setAsSecondary(true);
    actionGroup.addAction(new ShowLibraryContentsAction()).setAsSecondary(true);
  }

  @Override
  protected AbstractTreeUpdater createTreeUpdater(AbstractTreeBuilder treeBuilder) {
    return new PackageViewTreeUpdater(treeBuilder);
  }

  @Override
  public SelectInTarget createSelectInTarget() {
    return new PackagesPaneSelectInTarget(myProject);
  }

  @Override
  protected ProjectAbstractTreeStructureBase createStructure() {
    return new ProjectTreeStructure(myProject, ID){
      @Override
      protected AbstractTreeNode createRoot(final Project project, ViewSettings settings) {
        return new PackageViewProjectNode(project, settings);
      }
    };
  }

  @Override
  protected ProjectViewTree createTree(DefaultTreeModel treeModel) {
    return new ProjectViewTree(myProject, treeModel) {
      public String toString() {
        return getTitle() + " " + super.toString();
      }

      @Override
      public DefaultMutableTreeNode getSelectedNode() {
        return PackageViewPane.this.getSelectedNode();
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
    public boolean addSubtreeToUpdateByElement(Object element) {
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
      if (fileIndex.isInLibrarySource(vFile) || fileIndex.isInLibraryClasses(vFile)) {
        final List<OrderEntry> orderEntries = fileIndex.getOrderEntriesForFile(vFile);
        if (orderEntries.isEmpty()) {
          return Module.EMPTY_ARRAY;
        }
        for (OrderEntry entry : orderEntries) {
          modules.add(entry.getOwnerModule());
        }
      }
      return modules.toArray(new Module[modules.size()]);
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
      List<PsiDirectory> allElements = Arrays.asList(getSelectedDirectories());
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
}
