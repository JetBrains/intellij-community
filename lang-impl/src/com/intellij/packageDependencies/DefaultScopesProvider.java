/*
 * User: anna
 * Date: 17-Jan-2008
 */
package com.intellij.packageDependencies;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.FilePatternPackageSet;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DefaultScopesProvider implements CustomScopesProvider{
  private NamedScope myAllScope;


  public static DefaultScopesProvider getInstance(Project project) {
    for (CustomScopesProvider provider : Extensions.getExtensions(CUSTOM_SCOPES_PROVIDER, project)) {
      if (provider instanceof DefaultScopesProvider) return (DefaultScopesProvider)provider;
    }
    return null;
  }


  @NotNull
  public List<NamedScope> getCustomScopes() {
    final List<NamedScope> list = new ArrayList<NamedScope>();
    list.add(getAllScope());
    return list;
  }

  public NamedScope getAllScope() {
    if (myAllScope == null) {
      myAllScope = new NamedScope("All", new PackageSet() {
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
    return myAllScope;
  }
}