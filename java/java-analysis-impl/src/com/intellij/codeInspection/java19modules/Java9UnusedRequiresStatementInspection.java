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
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiJavaModuleReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashSet;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author Pavel.Dolgov
 */
public class Java9UnusedRequiresStatementInspection extends GlobalJavaBatchInspectionTool {
  private static final Logger LOG = Logger.getInstance(Java9UnusedRequiresStatementInspection.class);

  private static final Key<Optional<JavaModuleInfo>> JAVA_MODULE_INFO = Key.create("java_module_info");

  @Override
  public void runInspection(@NotNull AnalysisScope scope,
                            @NotNull InspectionManager manager,
                            @NotNull GlobalInspectionContext globalContext,
                            @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    super.runInspection(scope, manager, globalContext, problemDescriptionsProcessor);

    globalContext.getRefManager().iterate(new RefJavaVisitor(){
      @Override
      public void visitJavaModule(@NotNull RefJavaModule javaModule) {
        super.visitJavaModule(javaModule);

        RefModule refModule = javaModule.getModule();
        if (refModule != null) {
          JavaModuleInfo javaModuleInfo = getJavaModuleInfo(refModule, false);
          if (javaModuleInfo != null) {
            CommonProblemDescriptor[] descriptors = checkRequiredModules(javaModuleInfo, manager);
            if (descriptors != null) {
              problemDescriptionsProcessor.addProblemElement(javaModule, descriptors);
            }
          }
        }
      }
    });
  }

  @Nullable
  @Override
  public CommonProblemDescriptor[] checkElement(@NotNull RefEntity refEntity,
                                                @NotNull AnalysisScope scope,
                                                @NotNull InspectionManager manager,
                                                @NotNull GlobalInspectionContext globalContext) {
    if (refEntity instanceof RefClass) {
      RefModule refModule = ((RefClass)refEntity).getModule();
      if (refModule != null) {
        PsiClass aClass = ((RefClass)refEntity).getElement();
        PsiElement parent = aClass != null ? aClass.getParent() : null;
        if (parent instanceof PsiJavaFile) {
          checkJavaFile((PsiJavaFile)parent, refModule);
        }
      }
    }
    return null;
  }

  private static void checkJavaFile(@NotNull PsiJavaFile file, @NotNull RefModule refModule) {
    PsiImportList importList = file.getImportList();
    PsiImportStatementBase[] statements = importList != null ? importList.getAllImportStatements() : PsiImportStatementBase.EMPTY_ARRAY;
    if (statements.length != 0) {
      JavaModuleInfo javaModuleInfo = getJavaModuleInfo(refModule, true);
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
            javaModuleInfo.add(packageName);
          }
        }
      }
    }
  }

  @Nullable
  private static JavaModuleInfo getJavaModuleInfo(@NotNull RefModule refModule, boolean addIfMissing) {
    Optional<JavaModuleInfo> optionalInfo = refModule.getUserData(JAVA_MODULE_INFO);
    if (!addIfMissing) {
      return optionalInfo != null ? optionalInfo.orElse(null) : null;
    }
    if (optionalInfo == null) {
      optionalInfo = Optional
        .of(refModule)
        .map(RefModule::getModule)
        .map(JavaModuleGraphUtil::findDescriptorByModule)
        .map(JavaModuleInfo::new);
      refModule.putUserData(JAVA_MODULE_INFO, optionalInfo);
    }
    return optionalInfo.orElse(null);
  }

  private static CommonProblemDescriptor[] checkRequiredModules(@NotNull JavaModuleInfo javaModuleInfo,
                                                                @NotNull InspectionManager manager) {
    List<CommonProblemDescriptor> problems = new ArrayList<>();
    PsiJavaModule javaModule = javaModuleInfo.javaModule;
    String moduleName = javaModule.getModuleName();
    for (PsiRequiresStatement statement : javaModule.getRequires()) {
      PsiJavaModule dependency = PsiJavaModuleReference.resolve(statement, statement.getModuleName(), false);
      if (dependency != null && isDependencyUnused(dependency, javaModuleInfo.importedPackageNames, moduleName)) {
        String[] transitiveDependencyNames = getTransitiveDependencies(javaModuleInfo, dependency);
        ProblemDescriptor descriptor =
          manager.createProblemDescriptor(statement, "Unused statement 'requires " + dependency.getModuleName() + "'",
                                          new RemoveUnusedRequiresStatementFix(transitiveDependencyNames),
                                          ProblemHighlightType.LIKE_UNUSED_SYMBOL, false);
        problems.add(descriptor);
      }
    }
    return problems.isEmpty() ? null : problems.toArray(CommonProblemDescriptor.EMPTY_ARRAY);
  }

  private static boolean isDependencyUnused(@NotNull PsiJavaModule dependencyModule,
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

  private static class JavaModuleInfo { // TODO: remove this
    private final PsiJavaModule javaModule;
    private final Set<String> importedPackageNames;

    private JavaModuleInfo(@NotNull PsiJavaModule javaModule) {
      this.javaModule = javaModule;
      this.importedPackageNames = new THashSet<>();
    }

    private void add(String packageName) {
      importedPackageNames.add(packageName);
    }
  }
}
