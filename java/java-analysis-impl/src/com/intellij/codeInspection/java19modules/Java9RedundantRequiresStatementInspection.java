// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.java19modules;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.java.codeserver.core.JavaPsiModuleUtil;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UFile;
import org.jetbrains.uast.UImportStatement;
import org.jetbrains.uast.UastContextKt;

import java.util.*;
import java.util.stream.Stream;

public final class Java9RedundantRequiresStatementInspection extends GlobalJavaBatchInspectionTool {

  private static final Key<Set<String>> IMPORTED_JAVA_PACKAGES = Key.create("imported_java_packages");

  @Override
  public CommonProblemDescriptor @Nullable [] checkElement(@NotNull RefEntity refEntity,
                                                           @NotNull AnalysisScope scope,
                                                           @NotNull InspectionManager manager,
                                                           @NotNull GlobalInspectionContext globalContext) {
    if (refEntity instanceof RefJavaModule refJavaModule) {
      RefModule refModule = refJavaModule.getModule();
      PsiJavaModule psiJavaModule = refJavaModule.getPsiElement();
      if (refModule != null && psiJavaModule != null) {
        Set<String> moduleImportedPackages = refModule.getUserData(IMPORTED_JAVA_PACKAGES);
        if (moduleImportedPackages != null) {
          List<RefJavaModule.RequiredModule> requiredModules = refJavaModule.getRequiredModules();
          if (!requiredModules.isEmpty()) {
            List<CommonProblemDescriptor> descriptors = new ArrayList<>();
            for (RefJavaModule.RequiredModule requiredModule : requiredModules) {
              if (requiredModule.isTransitive()) continue;
              String requiredModuleName = requiredModule.moduleName();

              boolean isJavaBase = PsiJavaModule.JAVA_BASE.equals(requiredModuleName);
              if (isJavaBase ||
                  isDependencyUnused(requiredModule.packagesExportedByModule(), moduleImportedPackages, refJavaModule.getName())) {
                PsiRequiresStatement requiresStatement = ContainerUtil.find(
                  psiJavaModule.getRequires(), statement -> requiredModuleName.equals(statement.getModuleName()));
                if (requiresStatement != null && !isSuppressedFor(requiresStatement)) {
                  PsiJavaModule dependentModule = requiresStatement.resolve();
                  DeleteRedundantRequiresStatementFix requiresStatementFix =
                    new DeleteRedundantRequiresStatementFix(requiredModuleName, moduleImportedPackages, dependentModule, psiJavaModule);
                  String message = JavaAnalysisBundle.message("inspection.redundant.requires.statement.description", requiredModuleName) + " ";
                  if (isJavaBase) {
                    message += JavaAnalysisBundle.message("inspection.redundant.requires.statement.message.java.base.implicitly.required");
                  }
                  else if (!requiresStatementFix.hasReexportedDependencies()) {
                    message += JavaAnalysisBundle.message("inspection.redundant.requires.statement.message.module.unused");
                  }
                  else {
                    message += JavaAnalysisBundle.message("inspection.redundant.requires.statement.message.transitive.dependencies.on.can.be.used.directly",
                                                          StringUtil.join(requiresStatementFix.myDependencies, "', '"));
                  }
                  CommonProblemDescriptor descriptor = manager.createProblemDescriptor(
                    requiresStatement,
                    message,
                    requiresStatementFix,
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false);
                  descriptors.add(descriptor);
                }
              }
            }
            if (!ContainerUtil.isEmpty(descriptors)) {
              return descriptors.toArray(CommonProblemDescriptor.EMPTY_ARRAY);
            }
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

  @Override
  public @NotNull RefGraphAnnotator getAnnotator(@NotNull RefManager refManager) {
    return new RedundantRequiresStatementAnnotator();
  }

  private static class DeleteRedundantRequiresStatementFix extends PsiUpdateModCommandQuickFix {
    private final String myRequiredModuleName;
    private final Set<String> myImportedPackages;
    private final Set<String> myDependencies;

    DeleteRedundantRequiresStatementFix(String requiredModuleName, Set<String> importedPackages,
                                        PsiJavaModule dependentModule,
                                        PsiJavaModule currentModule) {
      myRequiredModuleName = requiredModuleName;
      myImportedPackages = importedPackages;
      myDependencies = getReexportedDependencies(currentModule, dependentModule);
    }
    
    boolean hasReexportedDependencies() {
      return !myDependencies.isEmpty();
    }

    @Override
    public @Nls @NotNull String getFamilyName() {
      return JavaAnalysisBundle.message("inspection.redundant.requires.statement.fix.family");
    }

    @Override
    public @Nls @NotNull String getName() {
      return JavaAnalysisBundle.message("inspection.redundant.requires.statement.fix.name", myRequiredModuleName);
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiRequiresStatement statementToDelete)) return;

      addTransitiveDependencies(statementToDelete);
      statementToDelete.delete();
    }

    private @NotNull Set<String> getReexportedDependencies(@NotNull PsiJavaModule currentModule, @NotNull PsiJavaModule dependencyModule) {
      Set<String> directDependencies = StreamEx
        .of(currentModule.getRequires().iterator())
        .map(PsiRequiresStatement::getModuleName)
        .nonNull()
        .toSet();

      List<PsiJavaModule> transitiveModules = StreamEx
        .of(dependencyModule.getRequires().iterator())
        .filter(statement -> statement.hasModifierProperty(PsiModifier.TRANSITIVE))
        .filter(requiresStatement -> !directDependencies.contains(requiresStatement.getModuleName()))
        .map(PsiRequiresStatement::resolve)
        .nonNull()
        .toList();

      return StreamEx.of(transitiveModules)
        .filter(transitiveModule -> isReexported(currentModule, transitiveModule))
        .map(transitiveModule -> transitiveModule.getName())
        .toSet();
    }

    private boolean isReexported(@NotNull PsiJavaModule currentModule, @NotNull PsiJavaModule transitiveModule) {
      return StreamEx
        .of(transitiveModule.getExports().iterator())
        .map(PsiPackageAccessibilityStatement::getPackageName)
        .nonNull()
        .filter(myImportedPackages::contains)
        .anyMatch(packageName -> JavaPsiModuleUtil.exports(transitiveModule, packageName, currentModule));
    }

    private void addTransitiveDependencies(@NotNull PsiRequiresStatement statementToDelete) {
      PsiElement parent = statementToDelete.getParent();
      if (parent instanceof PsiJavaModule currentModule) {
        PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(currentModule.getProject()).getParserFacade();
        for (String dependencyName : myDependencies) {
          PsiStatement requiresStatement = parserFacade.createModuleStatementFromText(JavaKeywords.REQUIRES + ' ' + dependencyName, null);
          currentModule.addAfter(requiresStatement, statementToDelete);
        }
      }
    }
  }

  private static class RedundantRequiresStatementAnnotator extends RefGraphAnnotator {
    private static final Set<String> DONT_COLLECT_PACKAGES = Collections.emptySet();

    @Override
    public void onReferencesBuild(RefElement refElement) {
      if (refElement instanceof RefFile refFile) {
        PsiFile file = refFile.getPsiElement();
        UFile uFile = UastContextKt.toUElement(file, UFile.class);
        if (uFile != null) {
          onJavaFileReferencesBuilt(refFile, uFile);
        }
      }
    }

    private static void onJavaFileReferencesBuilt(@NotNull RefFile refFile, UFile file) {
      RefModule refModule = refFile.getModule();
      if (refModule != null && LanguageLevelUtil.getEffectiveLanguageLevel(refModule.getModule()).isAtLeast(LanguageLevel.JDK_1_9)) {
        Set<String> packageNames = getImportedPackages(refModule);
        if (packageNames != DONT_COLLECT_PACKAGES) {
          Stream.concat(file.getImports().stream().map(st -> getPackageName(st)), 
                        file.getImplicitImports().stream())
            .filter(p -> !StringUtil.isEmpty(p))
            .forEach(packageNames::add);
        }
      }
    }

    private static @Nullable String getPackageName(UImportStatement statement) {
      PsiElement resolved = statement.resolve();
      if (resolved instanceof PsiPackage) {
        return ((PsiPackage)resolved).getQualifiedName();
      }
      else if (resolved != null) {
        UFile uFile = UastContextKt.getUastParentOfType(resolved, UFile.class);
        if (uFile != null) {
          return uFile.getPackageName();
        }
      }
      return null;
    }

    private static @NotNull Set<String> getImportedPackages(@NotNull RefModule refModule) {
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (refModule) {
        Set<String> importedPackages = refModule.getUserData(IMPORTED_JAVA_PACKAGES);
        if (importedPackages == null) {
          PsiJavaModule javaModule = JavaModuleGraphUtil.findDescriptorByModule(refModule.getModule(), false);
          if (javaModule == null) {
            javaModule = JavaModuleGraphUtil.findDescriptorByModule(refModule.getModule(), true);
          }
          importedPackages = javaModule != null ? ConcurrentCollectionFactory.createConcurrentSet() : DONT_COLLECT_PACKAGES;
          refModule.putUserData(IMPORTED_JAVA_PACKAGES, importedPackages);
        }
        return importedPackages;
      }
    }
  }
}
