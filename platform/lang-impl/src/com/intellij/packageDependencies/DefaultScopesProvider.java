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
import java.util.Arrays;
import java.util.List;

/**
 * @author anna
 * @author Konstantin Bulenkov
 */
public class DefaultScopesProvider implements CustomScopesProvider {
  private final NamedScope myProblemsScope;
  private final Project myProject;

  public static DefaultScopesProvider getInstance(Project project) {
    return Extensions.findExtension(CUSTOM_SCOPES_PROVIDER, project, DefaultScopesProvider.class);
  }

  public DefaultScopesProvider(Project project) {
    myProject = project;
    final String text = FilePatternPackageSet.SCOPE_FILE + ":*//*";
    myProblemsScope = new NamedScope(IdeBundle.message("predefined.scope.problems.name"), new AbstractPackageSet(text, myProject) {
      public boolean contains(PsiFile file, NamedScopesHolder holder) {
        return file.getProject() == myProject
               && WolfTheProblemSolver.getInstance(myProject).isProblemFile(file.getVirtualFile());
      }
    });
  }

  @NotNull
  public List<NamedScope> getCustomScopes() {
    return Arrays.asList(getProblemsScope(), getAllScope());
  }

  @SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
  private static class AllScopeHolder {
    private static final String TEXT = FilePatternPackageSet.SCOPE_FILE + ":*//*";
    private static final NamedScope ALL = new NamedScope("All", new AbstractPackageSet(TEXT, null, 0) {
      public boolean contains(final PsiFile file, final NamedScopesHolder scopesHolder) {
        return true;
      }
    });
  }

  public static NamedScope getAllScope() {
    return AllScopeHolder.ALL;
  }

  public NamedScope getProblemsScope() {
    return myProblemsScope;
  }

  public List<NamedScope> getAllCustomScopes() {
    final List<NamedScope> scopes = new ArrayList<NamedScope>();
    for (CustomScopesProvider provider : Extensions.getExtensions(CUSTOM_SCOPES_PROVIDER, myProject)) {
      scopes.addAll(provider.getCustomScopes());
    }
    return scopes;
  }
}
