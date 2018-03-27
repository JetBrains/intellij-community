// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  private static final Key<Set<String>> IMPORTED_JAVA_PACKAGES = Key.create("imported_java_packages");

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.redundant.requires.statement.name");
  }

  @Nullable
  @Override
  public CommonProblemDescriptor[] checkElement(@NotNull RefEntity refEntity,
                                                @NotNull AnalysisScope scope,
                                                @NotNull InspectionManager manager,
                                                @NotNull GlobalInspectionContext globalContext) {
    if (refEntity instanceof RefJavaModule) {
      RefJavaModule refJavaModule = (RefJavaModule)refEntity;

      RefModule refModule = refJavaModule.getModule();
      PsiJavaModule psiJavaModule = refJavaModule.getElement();
      if (refModule != null && psiJavaModule != null) {
        Set<String> moduleImportedPackages = refModule.getUserData(IMPORTED_JAVA_PACKAGES);
        if (moduleImportedPackages != null) {
          List<RefJavaModule.RequiredModule> requiredModules = refJavaModule.getRequiredModules();
          if (!requiredModules.isEmpty()) {
            List<CommonProblemDescriptor> descriptors = new ArrayList<>();
            for (RefJavaModule.RequiredModule requiredModule : requiredModules) {
              String requiredModuleName = requiredModule.moduleName;

              if (PsiJavaModule.JAVA_BASE.equals(requiredModuleName) ||
                  isDependencyUnused(requiredModule.packagesExportedByModule, moduleImportedPackages, refJavaModule.getName())) {
                PsiRequiresStatement requiresStatement = ContainerUtil.find(
                  psiJavaModule.getRequires(), statement -> requiredModuleName.equals(statement.getModuleName()));
                if (requiresStatement != null) {
                  CommonProblemDescriptor descriptor = manager.createProblemDescriptor(
                    requiresStatement,
                    InspectionsBundle.message("inspection.redundant.requires.statement.description", requiredModuleName),
                    new DeleteRedundantRequiresStatementFix(requiredModuleName, moduleImportedPackages),
                    ProblemHighlightType.LIKE_UNUSED_SYMBOL, false);
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

  @Nullable
  @Override
  public RefGraphAnnotator getAnnotator(@NotNull RefManager refManager) {
    return new RedundantRequiresStatementAnnotator();
  }

  private static PsiJavaModule resolveRequiredModule(PsiRequiresStatement requiresStatement) {
    return PsiJavaModuleReference.resolve(requiresStatement, requiresStatement.getModuleName(), false);
  }

  private static class DeleteRedundantRequiresStatementFix implements LocalQuickFix {
    private String myRequiredModuleName;
    private Set<String> myImportedPackages;

    public DeleteRedundantRequiresStatementFix(String requiredModuleName, Set<String> importedPackages) {
      myRequiredModuleName = requiredModuleName;
      myImportedPackages = importedPackages;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.redundant.requires.statement.fix.family");
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionsBundle.message("inspection.redundant.requires.statement.fix.name", myRequiredModuleName);
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
        .filter(statement -> statement.hasModifierProperty(PsiModifier.TRANSITIVE))
        .filter(requiresStatement -> !directDependencies.contains(requiresStatement.getModuleName()))
        .map(Java9RedundantRequiresStatementInspection::resolveRequiredModule)
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
          PsiStatement requiresStatement = parserFacade.createModuleStatementFromText(PsiKeyword.REQUIRES + ' ' + dependencyName);
          currentModule.addAfter(requiresStatement, addingPlace);
        }
      }
    }
  }

  private static class RedundantRequiresStatementAnnotator extends RefGraphAnnotator {
    private static final Set<String> DONT_COLLECT_PACKAGES = Collections.emptySet();

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
        RefModule refModule = refElement.getModule();
        if (refModule != null) {
          setImportedPackages(refModule, refElement.getElement() != null);
        }
      }
    }

    private static void onJavaFileReferencesBuilt(@NotNull RefFile refFile, @NotNull PsiJavaFile file) {
      if (file.getLanguageLevel().isAtLeast(LanguageLevel.JDK_1_9)) {
        PsiImportList importList = file.getImportList();
        if (importList != null) {
          RefModule refModule = refFile.getModule();
          if (refModule != null) {
            Set<String> packageNames = getImportedPackages(refModule, refFile);
            if (packageNames != DONT_COLLECT_PACKAGES) {
              PsiImportStatementBase[] statements = importList.getAllImportStatements();
              for (PsiImportStatementBase statement : statements) {
                String packageName = getPackageName(statement);
                if (!StringUtil.isEmpty(packageName)) {
                  packageNames.add(packageName);
                }
              }
            }
          }
        }
      }
    }

    @Nullable
    private static String getPackageName(@NotNull PsiImportStatementBase statement) {
      PsiElement resolved = statement.resolve();
      if (resolved instanceof PsiPackage) {
        return ((PsiPackage)resolved).getQualifiedName();
      }
      else if (resolved instanceof PsiMember) {
        PsiJavaFile parentFile = PsiTreeUtil.getParentOfType(resolved, PsiJavaFile.class);
        if (parentFile != null) {
          return parentFile.getPackageName();
        }
      }
      return null;
    }

    @NotNull
    private static Set<String> getImportedPackages(@NotNull RefModule refModule, @NotNull RefFile refFile) {
      Set<String> importedPackages = refModule.getUserData(IMPORTED_JAVA_PACKAGES);
      if (importedPackages == null) {
        PsiJavaModule javaModule = JavaModuleGraphUtil.findDescriptorByElement(refFile.getElement());
        importedPackages = javaModule != null ? new THashSet<>() : DONT_COLLECT_PACKAGES;
        refModule.putUserData(IMPORTED_JAVA_PACKAGES, importedPackages);
      }
      return importedPackages;
    }

    private static void setImportedPackages(RefModule refModule, boolean collectPackages) {
      Set<String> importedPackages = refModule.getUserData(IMPORTED_JAVA_PACKAGES);
      if (importedPackages == null) {
        refModule.putUserData(IMPORTED_JAVA_PACKAGES, collectPackages ? new THashSet<>() : DONT_COLLECT_PACKAGES);
      }
    }
  }
}
