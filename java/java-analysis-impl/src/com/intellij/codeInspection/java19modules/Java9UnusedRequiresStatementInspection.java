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
package com.intellij.codeInspection.java19modules;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiJavaModuleReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Pavel.Dolgov
 */
public class Java9UnusedRequiresStatementInspection extends GlobalJavaBatchInspectionTool {
  private static final Logger LOG = Logger.getInstance(Java9UnusedRequiresStatementInspection.class);

  private static final Key<Map<String, JavaModuleInfo>> MODULES_IN_PROJECT = Key.create("modules_in_project");

  @Nullable
  @Override
  public CommonProblemDescriptor[] checkElement(@NotNull RefEntity refEntity,
                                                @NotNull AnalysisScope scope,
                                                @NotNull InspectionManager manager,
                                                @NotNull GlobalInspectionContext globalContext) {
    // We depend here on the traversal order of the reference graph, unfortunately. The classes are walked before the modules.
    if (refEntity instanceof RefClass) {
      PsiClass aClass = ((RefClass)refEntity).getElement();
      PsiElement parent = aClass != null ? aClass.getParent() : null;
      if (parent instanceof PsiJavaFile) {
        checkJavaFile((PsiJavaFile)parent, refEntity.getRefManager());
      }
    }
    else if (refEntity instanceof RefModule) {
      RefModule refModule = (RefModule)refEntity;
      JavaModuleInfo javaModuleInfo = getJavaModuleInfo(refModule.getModule(), refModule.getRefManager());
      if (javaModuleInfo != null) {
        return checkRequiredModules(javaModuleInfo, manager);
      }
    }
    return null;
  }

  private static void checkJavaFile(@NotNull PsiJavaFile file, @NotNull RefManager refManager) {
    PsiImportList importList = file.getImportList();
    PsiImportStatementBase[] statements =
      importList != null ? importList.getAllImportStatements() : PsiImportStatementBase.EMPTY_ARRAY;
    if (statements.length != 0) {
      Module moduleForFile = ProjectFileIndex.SERVICE.getInstance(file.getProject()).getModuleForFile(file.getVirtualFile());
      if (moduleForFile != null) {
        JavaModuleInfo javaModuleInfo = getJavaModuleInfo(moduleForFile, refManager);
        if (javaModuleInfo != null) {
          for (PsiImportStatementBase statement : statements) {
            PsiElement resolved = statement.resolve();
            String packageName = null;
            if (resolved instanceof PsiPackage) {
              packageName = ((PsiPackage)resolved).getQualifiedName();
            }
            else if (resolved instanceof PsiMember) {
              PsiJavaFile parentFile = PsiTreeUtil.getParentOfType(resolved, PsiJavaFile.class);
              if (parentFile != null) {
                packageName = parentFile.getPackageName();
              }
            }
            if (!StringUtil.isEmpty(packageName)) {
              javaModuleInfo.importedPackageNames.add(packageName);
            }
          }
        }
      }
    }
  }

  private static CommonProblemDescriptor[] checkRequiredModules(@NotNull JavaModuleInfo javaModuleInfo,
                                                                @NotNull InspectionManager manager) {
    List<CommonProblemDescriptor> problems = new ArrayList<>();
    String moduleName = javaModuleInfo.javaModule.getModuleName();
    for (PsiRequiresStatement statement : javaModuleInfo.javaModule.getRequires()) {
      PsiJavaModule dependency = PsiJavaModuleReference.resolve(statement, statement.getModuleName(), false);
      if (dependency != null && isDependencyModuleUnused(dependency, javaModuleInfo.importedPackageNames, moduleName)) {
        String[] transitiveDependencyNames = getTransitiveDependencies(javaModuleInfo, dependency);
        ProblemDescriptor descriptor =
          manager.createProblemDescriptor(statement, "Unused 'requires' statement",
                                          new RemoveUnusedRequiresStatementFix(transitiveDependencyNames),
                                          ProblemHighlightType.LIKE_UNUSED_SYMBOL, false);
        problems.add(descriptor);
      }
    }
    return problems.isEmpty() ? null : problems.toArray(CommonProblemDescriptor.EMPTY_ARRAY);
  }

  private static boolean isDependencyModuleUnused(@NotNull PsiJavaModule dependencyModule,
                                                  @NotNull Set<String> importedPackageNames,
                                                  @NotNull String contextModuleName) {
    for (PsiExportsStatement exportsStatement : dependencyModule.getExports()) {
      List<String> exportedToModules = exportsStatement.getModuleNames();
      if (!exportedToModules.isEmpty() && !exportedToModules.contains(contextModuleName)) {
        continue; // exported to other modules but not this one
      }
      String packageName = exportsStatement.getPackageName();
      if (packageName == null) {
        return false; // module info is incomplete or has an error
      }
      if (importedPackageNames.contains(packageName)) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  private static String[] getTransitiveDependencies(@NotNull JavaModuleInfo javaModuleInfo, @NotNull PsiJavaModule dependency) {
    Set<String> result = new THashSet<>();
    for (PsiRequiresStatement statement : dependency.getRequires()) {
      if (statement.isPublic()) {
        PsiJavaModule transitiveDependency = PsiJavaModuleReference.resolve(statement, statement.getModuleName(), false);
        if (transitiveDependency != null) {
          for (String packageName : javaModuleInfo.importedPackageNames) {
            if (JavaModuleGraphUtil.exports(transitiveDependency, packageName, javaModuleInfo.javaModule)) {
              result.add(transitiveDependency.getModuleName());
            }
          }
        }
      }
    }
    return ArrayUtil.toStringArray(result);
  }

  @NotNull
  private static Map<String, JavaModuleInfo> getModulesInProject(@NotNull RefManager refManager) {
    RefProject refProject = refManager.getRefProject();
    Map<String, JavaModuleInfo> javaModules = refProject.getUserData(MODULES_IN_PROJECT);
    if (javaModules == null) {
      javaModules = new THashMap<>();
      refProject.putUserData(MODULES_IN_PROJECT, javaModules);
    }
    return javaModules;
  }

  private static JavaModuleInfo getJavaModuleInfo(@NotNull Module module, @NotNull RefManager refManager) {
    Map<String, JavaModuleInfo> modulesInProject = getModulesInProject(refManager);
    String moduleName = module.getName();
    JavaModuleInfo javaModuleInfo = modulesInProject.get(moduleName);
    if (javaModuleInfo != null) {
      return javaModuleInfo;
    }
    if (modulesInProject.containsKey(moduleName)) {
      return null;
    }
    PsiJavaModule javaModule = JavaModuleGraphUtil.findDescriptorByModule(module);
    javaModuleInfo = javaModule != null ? new JavaModuleInfo(javaModule) : null;
    modulesInProject.put(moduleName, javaModuleInfo);
    return javaModuleInfo;
  }

  private static class RemoveUnusedRequiresStatementFix implements LocalQuickFix {
    private final String[] myTransitiveDependencies;

    public RemoveUnusedRequiresStatementFix(@NotNull String[] transitiveDependencies) {
      myTransitiveDependencies = transitiveDependencies;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Remove unused 'requires' statement";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;

      LOG.assertTrue(element instanceof PsiRequiresStatement);
      PsiRequiresStatement statementToDelete = (PsiRequiresStatement)element;
      addTransitiveDependencies(project, statementToDelete);
      statementToDelete.delete();
    }

    private void addTransitiveDependencies(@NotNull Project project, PsiRequiresStatement statementToDelete) {
      if (myTransitiveDependencies.length != 0) {
        PsiElement parent = statementToDelete.getParent();
        LOG.assertTrue(parent instanceof PsiJavaModule);
        PsiJavaModule module = (PsiJavaModule)parent;

        Set<String> directDependencies = StreamEx
          .of(module.getRequires().iterator())
          .map(PsiRequiresStatement::getModuleName)
          .nonNull()
          .toSet();

        List<String> missingTransitiveDependencies = StreamEx
          .of(myTransitiveDependencies)
          .filter(name -> !directDependencies.contains(name))
          .toList();

        PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(project).getParserFacade();
        for (String dependencyName : missingTransitiveDependencies) {
          PsiJavaModule tempModule =
            parserFacade.createModuleFromText("module " + module.getModuleName() + " { requires " + dependencyName + "; }");
          Iterable<PsiRequiresStatement> tempModuleRequires = tempModule.getRequires();
          PsiRequiresStatement requiresStatement = tempModuleRequires.iterator().next();
          module.addAfter(requiresStatement, statementToDelete);
        }
      }
    }
  }

  private static class JavaModuleInfo {
    private final PsiJavaModule javaModule;
    private final Set<String> importedPackageNames;

    private JavaModuleInfo(@NotNull PsiJavaModule javaModule) {
      this.javaModule = javaModule;
      this.importedPackageNames = new THashSet<>();
    }
  }
}
