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
package com.intellij.packageDependencies;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.scope.packageSet.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author anna
 */
public class ChangeListsScopesProvider extends CustomScopesProviderEx {
  private Project myProject;

  public static ChangeListsScopesProvider getInstance(Project project) {
    return Extensions.findExtension(CUSTOM_SCOPES_PROVIDER, project, ChangeListsScopesProvider.class);
  }

  public ChangeListsScopesProvider(Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public List<NamedScope> getCustomScopes() {

    if (myProject.isDefault()) return Collections.emptyList();
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);

    final List<NamedScope> result = new ArrayList<NamedScope>();
    result.add(createScope(changeListManager.getAffectedFiles(), IdeBundle.message("scope.modified.files")));
    for (ChangeList list : changeListManager.getChangeListsCopy()) {
      result.add(createChangeListScope(list));
    }
    return result;
  }

  private static NamedScope createChangeListScope(@NotNull ChangeList list) {
    final List<VirtualFile> files = new ArrayList<VirtualFile>();
    final Collection<Change> changes = list.getChanges();
    for (Change change : changes) {
      final ContentRevision afterRevision = change.getAfterRevision();
      if (afterRevision != null) {
        final VirtualFile vFile = afterRevision.getFile().getVirtualFile();
        if (vFile != null) {
          files.add(vFile);
        }
      }
    }
    return createScope(files, list.getName());
  }

  @Override
  public NamedScope getCustomScope(String name) {
    if (myProject.isDefault()) return null;
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    if (IdeBundle.message("scope.modified.files").equals(name)) {
      return createScope(changeListManager.getAffectedFiles(), IdeBundle.message("scope.modified.files"));
    }
    final LocalChangeList changeList = changeListManager.findChangeList(name);
    if (changeList != null) {
      return createChangeListScope(changeList);
    }
    return null;
  }

  private static NamedScope createScope(final List<VirtualFile> files, String changeListName) {
    return new NamedScope(changeListName, new PackageSetBase() {
      @Override
      public boolean contains(VirtualFile file, NamedScopesHolder holder) {
        return files.contains(file);
      }

      @Override
      public PackageSet createCopy() {
        return this;
      }

      @Override
      public String getText() {
        return "file:*//*";
      }

      @Override
      public int getNodePriority() {
        return 0;
      }
    }){
      @Override
      public boolean equals(Object obj) {
        if (!(obj instanceof NamedScope)) return false;
        return getName().equals(((NamedScope)obj).getName());
      }

      @Override
      public int hashCode() {
        return getName().hashCode();
      }
    };
  }
}
