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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiJavaModuleReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Pavel.Dolgov
 */
public class Java9RedundantRequiresStatementInspection extends GlobalJavaBatchInspectionTool {
  private static final Logger LOG = Logger.getInstance(Java9RedundantRequiresStatementInspection.class);

  // applicable to RefFile and RefModule
  private static final Key<Set<String>> IMPORTED_JAVA_PACKAGES = Key.create("imported_java_packages");

  // applicable to RefProject
  // mappings: module name -> exported package name -> export to modules
  private static final Key<Map<String, Map<String, List<String>>>> EXPORTED_MODULE_PACKAGES =
    Key.create("exported_module_packages");


  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.redundant.requires.statement.name");
  }

  @Override
  public void runInspection(@NotNull AnalysisScope scope,
                            @NotNull InspectionManager manager,
                            @NotNull GlobalInspectionContext globalContext,
                            @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    super.runInspection(scope, manager, globalContext, problemDescriptionsProcessor);
    globalContext.getRefManager().iterate(new RedundantRequiresStatementVisitor(manager, problemDescriptionsProcessor));
  }

  @Nullable
  @Override
  public CommonProblemDescriptor[] checkElement(@NotNull RefEntity refEntity,
                                                @NotNull AnalysisScope scope,
                                                @NotNull InspectionManager manager,
                                                @NotNull GlobalInspectionContext globalContext) {
    if (refEntity instanceof RefFile) {
      RefModule refModule = ((RefFile)refEntity).getModule();
      if (refModule != null) {
        Set<String> importedPackages = refEntity.getUserData(IMPORTED_JAVA_PACKAGES);
        if (!ContainerUtil.isEmpty(importedPackages)) {
          Set<String> moduleImportedPackages = refModule.getUserData(IMPORTED_JAVA_PACKAGES);
          if (moduleImportedPackages != null) {
            moduleImportedPackages.addAll(importedPackages);
          }
        }
      }
    }
    return null;
  }

  private static boolean isDependencyUnused(@NotNull Map<String, List<String>> dependencyExportedPackages,
                                            @NotNull Set<String> importedPackageNames,
                                            @NotNull String contextModuleName) {
    for (Map.Entry<String, List<String>> entry : dependencyExportedPackages.entrySet()) {
      String exportedPackageName = entry.getKey();
      List<String> exportedToModules = entry.getValue();
      if (!exportedToModules.isEmpty() && !exportedToModules.contains(contextModuleName)) {
        continue; // exported to some other modules but not to this one
      }
      if (importedPackageNames.contains(exportedPackageName)) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  @Override
  public RefGraphAnnotator getAnnotator(@NotNull RefManager refManager) {
    return new RedundantRequiresStatementAnnotator();
  }

  @NotNull
  private static Map<String, Map<String, List<String>>> getExportedModulePackages(RefManager refManager) {
    RefProject refProject = refManager.getRefProject();
    Map<String, Map<String, List<String>>> exportedModulePackages = refProject.getUserData(EXPORTED_MODULE_PACKAGES);
    if (exportedModulePackages == null) {
      exportedModulePackages = new THashMap<>();
      refProject.putUserData(EXPORTED_MODULE_PACKAGES, exportedModulePackages);
    }
    return exportedModulePackages;
  }

  private static PsiJavaModule resolveRequiredModule(PsiRequiresStatement requiresStatement) {
    return PsiJavaModuleReference.resolve(requiresStatement, requiresStatement.getModuleName(), false);
  }

  private static class DeleteRedundantRequiresStatementFix implements LocalQuickFix {
    private Set<String> myImportedPackages;

    public DeleteRedundantRequiresStatementFix(Set<String> importedPackages) {
      myImportedPackages = importedPackages;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.redundant.requires.statement.fix.family");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;

      LOG.assertTrue(element instanceof PsiRequiresStatement, "Should be 'requires' statement");
      PsiRequiresStatement statementToDelete = (PsiRequiresStatement)element;
      addTransitiveDependencies(statementToDelete);
      statementToDelete.delete();
    }

    private Set<String> getReexportedDependencies(@NotNull PsiJavaModule currentModule, @NotNull PsiJavaModule dependencyModule) {
      Set<String> directDependencies = StreamEx
        .of(currentModule.getRequires().iterator())
        .map(PsiRequiresStatement::getModuleName)
        .nonNull()
        .toSet();

      List<PsiJavaModule> transitiveModules = StreamEx
        .of(dependencyModule.getRequires().iterator())
        .filter(PsiRequiresStatement::isPublic)
        .filter(requiresStatement -> !directDependencies.contains(requiresStatement.getModuleName()))
        .map(Java9RedundantRequiresStatementInspection::resolveRequiredModule)
        .nonNull()
        .toList();

      return StreamEx.of(transitiveModules)
        .filter(transitiveModule -> isReexported(currentModule, transitiveModule))
        .map(transitiveModule -> transitiveModule.getModuleName())
        .toSet();
    }

    private boolean isReexported(@NotNull PsiJavaModule currentModule, @NotNull PsiJavaModule transitiveModule) {
      return StreamEx
        .of(transitiveModule.getExports().iterator())
        .map(PsiExportsStatement::getPackageName)
        .nonNull()
        .filter(myImportedPackages::contains)
        .anyMatch(packageName -> JavaModuleGraphUtil.exports(transitiveModule, packageName, currentModule));
    }

    private void addTransitiveDependencies(@NotNull PsiRequiresStatement statementToDelete) {
      PsiElement parent = statementToDelete.getParent();
      if (parent instanceof PsiJavaModule) {
        PsiJavaModule currentModule = (PsiJavaModule)parent;
        Optional.of(statementToDelete)
          .map(Java9RedundantRequiresStatementInspection::resolveRequiredModule)
          .map(dependencyModule -> getReexportedDependencies(currentModule, dependencyModule))
          .ifPresent(reexportedDependencies -> addReexportedDependencies(reexportedDependencies, currentModule, statementToDelete));
      }
    }

    private static void addReexportedDependencies(@NotNull Set<String> reexportedDependencies,
                                                  @NotNull PsiJavaModule currentModule,
                                                  @NotNull PsiElement addingPlace) {
      if (!reexportedDependencies.isEmpty()) {
        PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(currentModule.getProject()).getParserFacade();
        for (String dependencyName : reexportedDependencies) {
          PsiJavaModule tempModule =
            parserFacade.createModuleFromText("module " + currentModule.getModuleName() + " { requires " + dependencyName + "; }");
          Iterable<PsiRequiresStatement> tempModuleRequires = tempModule.getRequires();
          PsiRequiresStatement requiresStatement = tempModuleRequires.iterator().next();
          currentModule.addAfter(requiresStatement, addingPlace);
        }
      }
    }
  }

  private static class RedundantRequiresStatementVisitor extends RefJavaVisitor {
    private final InspectionManager myManager;
    private final ProblemDescriptionsProcessor myProblemDescriptionsProcessor;

    public RedundantRequiresStatementVisitor(InspectionManager manager, ProblemDescriptionsProcessor problemDescriptionsProcessor) {
      myManager = manager;
      myProblemDescriptionsProcessor = problemDescriptionsProcessor;
    }

    @Override
    public void visitJavaModule(@NotNull RefJavaModule refJavaModule) {
      super.visitJavaModule(refJavaModule);

      RefModule refModule = refJavaModule.getModule();
      if (refModule != null) {
        Set<String> moduleImportedPackages = refModule.getUserData(IMPORTED_JAVA_PACKAGES);
        if (moduleImportedPackages != null) {
          Map<String, Boolean> requiredModuleNames = refJavaModule.getRequiredModuleNames();
          if (!requiredModuleNames.isEmpty()) {
            Map<String, Map<String, List<String>>> exportedModulePackages = getExportedModulePackages(refJavaModule.getRefManager());
            for (String dependencyModuleName : requiredModuleNames.keySet()) {
              Map<String, List<String>> exportedPackages = exportedModulePackages.get(dependencyModuleName);
              if (exportedPackages != null && isDependencyUnused(exportedPackages, moduleImportedPackages, refJavaModule.getName())) {
                PsiRequiresStatement statement = refJavaModule.getRequiresStatements().get(dependencyModuleName);
                if (statement != null) {
                  registerProblem(refJavaModule, statement, moduleImportedPackages, dependencyModuleName);
                }
              }
            }
          }
        }
      }
    }

    private void registerProblem(@NotNull RefJavaModule refJavaModule,
                                 @NotNull PsiRequiresStatement requiresStatement,
                                 @NotNull Set<String> importedPackages,
                                 @NotNull String dependencyModuleName) {
      ProblemDescriptor descriptor =
        myManager.createProblemDescriptor(requiresStatement,
                                          InspectionsBundle
                                            .message("inspection.redundant.requires.statement.description", dependencyModuleName),
                                          new DeleteRedundantRequiresStatementFix(importedPackages),
                                          ProblemHighlightType.LIKE_UNUSED_SYMBOL, false);
      myProblemDescriptionsProcessor.addProblemElement(refJavaModule, descriptor);
    }
  }

  private static class RedundantRequiresStatementAnnotator extends RefGraphAnnotator {

    @Override
    public void onReferencesBuild(RefElement refElement) {
      if (refElement instanceof RefFile) {
        RefFile refFile = (RefFile)refElement;
        PsiFile file = refFile.getElement();
        if (file instanceof PsiJavaFile) {
          onJavaFileReferencesBuilt(refFile, (PsiJavaFile)file);
        }
      }
      else if (refElement instanceof RefJavaModule) {
        PsiJavaModule javaModule = ((RefJavaModule)refElement).getElement();
        if (javaModule != null) {
          RefModule refModule = refElement.getModule();
          if (refModule != null) {
            onJavaModuleReferencesBuilt(javaModule, refModule);
          }
        }
      }
    }

    private static void onJavaFileReferencesBuilt(@NotNull RefFile refFile, @NotNull PsiJavaFile file) {
      if (file.getLanguageLevel().isAtLeast(LanguageLevel.JDK_1_9)) {
        PsiImportList importList = file.getImportList();
        if (importList != null) {
          Set<String> packageNames = new THashSet<>();
          PsiImportStatementBase[] statements = importList.getAllImportStatements();
          if (statements.length != 0) {
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
                packageNames.add(packageName);
              }
              if (!packageNames.isEmpty()) {
                refFile.putUserData(IMPORTED_JAVA_PACKAGES, packageNames);
              }
            }
          }
        }
      }
    }

    private static void onJavaModuleReferencesBuilt(@NotNull PsiJavaModule javaModule, @NotNull RefModule refModule) {
      LOG.assertTrue(refModule.getUserData(IMPORTED_JAVA_PACKAGES) == null, "Duplicate Java module declaration");
      refModule.putUserData(IMPORTED_JAVA_PACKAGES, new THashSet<>());

      Map<String, Map<String, List<String>>> exportedModulePackages = getExportedModulePackages(refModule.getRefManager());
      for (PsiRequiresStatement statement : javaModule.getRequires()) {
        String dependencyModuleName = statement.getModuleName();
        if (dependencyModuleName != null) {
          Map<String, List<String>> exportedPackages = exportedModulePackages.get(dependencyModuleName);
          if (exportedPackages == null) {
            PsiJavaModule dependency = resolveRequiredModule(statement);
            exportedPackages = getExportedPackages(dependency);
            exportedModulePackages.put(dependencyModuleName, exportedPackages);
          }
        }
      }
    }

    @NotNull
    private static Map<String, List<String>> getExportedPackages(@Nullable PsiJavaModule javaModule) {
      if (javaModule == null) {
        return Collections.emptyMap();
      }
      Map<String, List<String>> exportedPackages = new THashMap<>();
      for (PsiExportsStatement statement : javaModule.getExports()) {
        String packageName = statement.getPackageName();
        if (packageName != null) {
          exportedPackages.put(packageName, statement.getModuleNames());
        }
      }
      return !exportedPackages.isEmpty() ? exportedPackages : Collections.emptyMap();
    }
  }
}
