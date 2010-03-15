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
 * User: anna
 * Date: 17-Jan-2008
 */
package com.intellij.packageDependencies;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DefaultScopesProvider implements CustomScopesProvider {
  private NamedScope myProblemsScope;
  private final Project myProject;

  public static DefaultScopesProvider getInstance(Project project) {
    for (CustomScopesProvider provider : Extensions.getExtensions(CUSTOM_SCOPES_PROVIDER, project)) {
      if (provider instanceof DefaultScopesProvider) return (DefaultScopesProvider)provider;
    }
    return null;
  }

  public DefaultScopesProvider(Project project) {
    myProject = project;
  }

  @NotNull
  public List<NamedScope> getCustomScopes() {
    final List<NamedScope> list = new ArrayList<NamedScope>();
    list.add(getProblemsScope());
    list.add(getAllScope());
    return list;
  }

  private static class NamedScopeHolder {
    private static final NamedScope myAllScope = new NamedScope("All", new PackageSet() {
      public boolean contains(final PsiFile file, final NamedScopesHolder holder) {
        return true;
      }

      public PackageSet createCopy() {
        return this;
      }

      public String getText() {
        return FilePatternPackageSet.SCOPE_FILE + ":*//*";
      }

      public int getNodePriority() {
        return 0;
      }
    });
  }

  public static NamedScope getAllScope() {
    return NamedScopeHolder.myAllScope;
  }

   public NamedScope getProblemsScope() {
    if (myProblemsScope == null) {
      myProblemsScope = new NamedScope(IdeBundle.message("predefined.scope.problems.name"), new PackageSet() {
        public boolean contains(PsiFile file, NamedScopesHolder holder) {
          return file.getProject() == myProject && WolfTheProblemSolver.getInstance(myProject).isProblemFile(file.getVirtualFile());
        }

        public PackageSet createCopy() {
          return this;
        }

        public String getText() {
          return FilePatternPackageSet.SCOPE_FILE + ":*//*";
        }

        public int getNodePriority() {
          return 1;
        }
      });
    }
    return myProblemsScope;
  }
}
