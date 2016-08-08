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

/*
 * @author max
 */
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.util.PathUtil;
import com.intellij.util.PlatformIcons;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class ExternalLibrariesNode extends ProjectViewNode<String> {
  public ExternalLibrariesNode(Project project, ViewSettings viewSettings) {
    super(project, "External Libraries", viewSettings);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    ProjectFileIndex index = ProjectRootManager.getInstance(getProject()).getFileIndex();
    if (!index.isInLibrarySource(file) && !index.isInLibraryClasses(file)) return false;

    return someChildContainsFile(file, false);
  }

  @NotNull
  @Override
  public Collection<? extends AbstractTreeNode> getChildren() {
    final List<AbstractTreeNode> children = new ArrayList<>();
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(getProject()).getFileIndex();
    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    Set<Library> processedLibraries = new THashSet<>();
    Set<Sdk> processedSdk = new THashSet<>();

    for (Module module : modules) {
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      final OrderEntry[] orderEntries = moduleRootManager.getOrderEntries();
      for (final OrderEntry orderEntry : orderEntries) {
        if (orderEntry instanceof LibraryOrderEntry) {
          final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)orderEntry;
          final Library library = libraryOrderEntry.getLibrary();
          if (library == null) continue;
          if (processedLibraries.contains(library)) continue;
          processedLibraries.add(library);

          if (!hasExternalEntries(fileIndex, libraryOrderEntry)) continue;

          final String libraryName = library.getName();
          if (libraryName == null || libraryName.length() == 0) {
            addLibraryChildren(libraryOrderEntry, children, getProject(), this);
          }
          else {
            children.add(new NamedLibraryElementNode(getProject(), new NamedLibraryElement(null, libraryOrderEntry), getSettings()));
          }
        }
        else if (orderEntry instanceof JdkOrderEntry) {
          final JdkOrderEntry jdkOrderEntry = (JdkOrderEntry)orderEntry;
          final Sdk jdk = jdkOrderEntry.getJdk();
          if (jdk != null) {
            if (processedSdk.contains(jdk)) continue;
            processedSdk.add(jdk);
            children.add(new NamedLibraryElementNode(getProject(), new NamedLibraryElement(null, jdkOrderEntry), getSettings()));
          }
        }
      }
    }
    return children;
  }

  public static void addLibraryChildren(final LibraryOrderEntry entry, final List<AbstractTreeNode> children, Project project, ProjectViewNode node) {
    final PsiManager psiManager = PsiManager.getInstance(project);
    final VirtualFile[] files = entry.getRootFiles(OrderRootType.CLASSES);
    for (final VirtualFile file : files) {
      final PsiDirectory psiDir = psiManager.findDirectory(file);
      if (psiDir == null) {
        continue;
      }
      children.add(new PsiDirectoryNode(project, psiDir, node.getSettings()));
    }
  }

  private static boolean hasExternalEntries(ProjectFileIndex index, LibraryOrderEntry orderEntry) {
    for (VirtualFile file : LibraryGroupNode.getLibraryRoots(orderEntry)) {
      if (!index.isInContent(PathUtil.getLocalFile(file))) return true;
    }
    return false;
  }


  @Override
  protected void update(PresentationData presentation) {
    presentation.setPresentableText(IdeBundle.message("node.projectview.external.libraries"));
    presentation.setIcon(PlatformIcons.LIBRARY_ICON);
  }
}
