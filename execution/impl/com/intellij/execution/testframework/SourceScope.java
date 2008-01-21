package com.intellij.execution.testframework;

import com.intellij.execution.junit.JUnitUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * @author dyoma
 */
public abstract class SourceScope {
  public abstract GlobalSearchScope getGlobalSearchScope();
  public abstract Project getProject();
  public abstract GlobalSearchScope getLibrariesScope();

  private abstract static class ModuleSourceScope extends SourceScope {
    private final Project myProject;

    protected ModuleSourceScope(final Project project) {
      myProject = project;
    }

    public Project getProject() {
      return myProject;
    }

  }

  public static SourceScope wholeProject(final Project project) {
    return new SourceScope() {
      public GlobalSearchScope getGlobalSearchScope() {
        return GlobalSearchScope.allScope(project);
      }

      public Project getProject() {
        return project;
      }

      public Module[] getModulesToCompile() {
        return ModuleManager.getInstance(project).getModules();
      }

      public GlobalSearchScope getLibrariesScope() {
        return getGlobalSearchScope();
      }
    };
  }

  public static SourceScope modulesWithDependencies(final Module[] modules) {
    if (modules == null || modules.length == 0) return null;
    return new ModuleSourceScope(modules[0].getProject()) {
      public GlobalSearchScope getGlobalSearchScope() {
        return evaluateScopesAndUnite(modules, new ScopeForModuleEvaluator() {
          public GlobalSearchScope evaluate(final Module module) {
            return GlobalSearchScope.moduleWithDependenciesScope(module);
          }
        });
      }

      public GlobalSearchScope getLibrariesScope() {
        return evaluateScopesAndUnite(modules, new ScopeForModuleEvaluator() {
          public GlobalSearchScope evaluate(final Module module) {
            return new ModuleWithDependenciesAndLibsDependencies(module);
          }
        });
      }

      public Module[] getModulesToCompile() {
        return modules;
      }
    };
  }

  private interface ScopeForModuleEvaluator {
    GlobalSearchScope evaluate(Module module);
  }
  private static GlobalSearchScope evaluateScopesAndUnite(final Module[] modules, final ScopeForModuleEvaluator evaluator) {
    GlobalSearchScope scope = evaluator.evaluate(modules[0]);
    for (int i = 1; i < modules.length; i++) {
      final Module module = modules[i];
      final GlobalSearchScope otherscope = evaluator.evaluate(module);
      scope = scope.uniteWith(otherscope);
    }
    return scope;
  }

  public static SourceScope modules(final Module[] modules) {
    if (modules == null || modules.length == 0) return null;
    return new ModuleSourceScope(modules[0].getProject()) {
      public GlobalSearchScope getGlobalSearchScope() {
        return evaluateScopesAndUnite(modules, new ScopeForModuleEvaluator() {
          public GlobalSearchScope evaluate(final Module module) {
            return GlobalSearchScope.moduleScope(module);
          }
        });
      }

      public GlobalSearchScope getLibrariesScope() {
        return evaluateScopesAndUnite(modules, new ScopeForModuleEvaluator() {
          public GlobalSearchScope evaluate(final Module module) {
            return GlobalSearchScope.moduleWithLibrariesScope(module);
          }
        });
      }

      public Module[] getModulesToCompile() {
        return modules;
      }
    };
  }

  public abstract Module[] getModulesToCompile();

  private static class ModuleWithDependenciesAndLibsDependencies extends GlobalSearchScope {
    private final GlobalSearchScope myMainScope;
    private final Collection<GlobalSearchScope> myScopes = new ArrayList<GlobalSearchScope>();

    public ModuleWithDependenciesAndLibsDependencies(final Module module) {
      myMainScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
      final Map<Module, Collection<Module>> map = JUnitUtil.buildAllDependencies(module.getProject());
      if (map == null) return;
      final Collection<Module> modules = map.get(module);
      for (final Module dependency : modules) {
        myScopes.add(GlobalSearchScope.moduleWithLibrariesScope(dependency));
      }
    }

    public boolean contains(final VirtualFile file) {
      return findScopeFor(file) != null;
    }

    public int compare(final VirtualFile file1, final VirtualFile file2) {
      final GlobalSearchScope scope = findScopeFor(file1);
      if (scope.contains(file2)) return scope.compare(file1, file2);
      return 0;
    }

    public boolean isSearchInModuleContent(@NotNull final Module aModule) {
      return myMainScope.isSearchInModuleContent(aModule);
    }

    public boolean isSearchInLibraries() {
      return true;
    }

    private GlobalSearchScope findScopeFor(final VirtualFile file) {
      if (myMainScope.contains(file)) return myMainScope;
      for (final GlobalSearchScope scope : myScopes) {
        if (scope.contains(file)) return scope;
      }
      return null;
    }
  }
}
