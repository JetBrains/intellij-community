// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.unnecessaryModuleDependency;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.JobDescriptor;
import com.intellij.codeInspection.reference.*;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifier;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class UnnecessaryModuleDependencyInspection extends GlobalInspectionTool {
  @Override
  public RefGraphAnnotator getAnnotator(@NotNull final RefManager refManager) {
    return new UnnecessaryModuleDependencyAnnotator(refManager);
  }

  @Override
  public JobDescriptor @Nullable [] getAdditionalJobs(@NotNull GlobalInspectionContext context) {
    return JobDescriptor.EMPTY_ARRAY;
  }

  @Override
  public boolean queryExternalUsagesRequests(@NotNull InspectionManager manager,
                                             @NotNull GlobalInspectionContext globalContext,
                                             @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    GlobalJavaInspectionContext javaInspectionContext = globalContext.getExtension(GlobalJavaInspectionContext.CONTEXT);
    if (javaInspectionContext != null) {
      final RefManager refManager = globalContext.getRefManager();
      Map<String, Set<String>> to2FromCandidatePairsToRemove = new HashMap<>();
      for (Module module : ModuleManager.getInstance(refManager.getProject()).getModules()) {
        RefModule refModule = refManager.getRefModule(module);
        CommonProblemDescriptor[] descriptions = problemDescriptionsProcessor.getDescriptions(Objects.requireNonNull(refModule));
        if (descriptions != null) {
          String sourceModuleName = module.getName();
          for (CommonProblemDescriptor description : descriptions) {
            QuickFix<?>[] fixes = description.getFixes();
            if (fixes != null) {
              Arrays.stream(fixes)
                .map(fix -> fix instanceof RemoveModuleDependencyFix ? ((RemoveModuleDependencyFix)fix).myDependency : null)
                .filter(Objects::nonNull)
                .forEach(targetName -> to2FromCandidatePairsToRemove.computeIfAbsent(targetName, k -> new HashSet<>()).add(sourceModuleName));
            }
          }
        }
      }

      refManager.iterate(new RefJavaVisitor() {
        @Override
        public void visitClass(@NotNull RefClass aClass) {
          if (aClass.isAnonymous() || aClass.isLocalClass()) return;
          RefModule toModule = aClass.getModule();
          if (toModule == null) return;
          String toModuleName = toModule.getName();
          if (!to2FromCandidatePairsToRemove.containsKey(toModuleName)) return;
          if (PsiModifier.PRIVATE.equals(aClass.getAccessModifier())) return;
          javaInspectionContext.enqueueClassUsagesProcessor(aClass, reference -> {
            PsiFile containingFile = reference.getElement().getContainingFile();
            if (!(containingFile instanceof PsiClassOwner)) {
              RefElement refFrom = refManager.getReference(containingFile);
              if (refFrom != null) {
                RefModule fromModule = refFrom.getModule();
                if (fromModule != null) {
                  CommonProblemDescriptor[] descriptions = problemDescriptionsProcessor.getDescriptions(fromModule);
                  if (descriptions != null) {
                    LinkedHashSet<CommonProblemDescriptor> problemDescriptors = new LinkedHashSet<>(Arrays.asList(descriptions));
                    boolean removed = problemDescriptors.removeIf(descriptor -> {
                      QuickFix<?>[] fixes = descriptor.getFixes();
                      return fixes != null && ContainerUtil.exists(fixes, fix -> fix instanceof RemoveModuleDependencyFix && 
                                                                                 toModuleName.equals(((RemoveModuleDependencyFix)fix).myDependency));
                    });
                    if (removed) {
                      problemDescriptionsProcessor.ignoreElement(fromModule);
                      if (!problemDescriptors.isEmpty()) {
                        problemDescriptionsProcessor.addProblemElement(fromModule, problemDescriptors.toArray(CommonProblemDescriptor[]::new));
                      }
                    }
                  }
                }
              }
            }
            return true;
          });
        }
      });
    }
    return false;
  }

  @Override
  public CommonProblemDescriptor[] checkElement(@NotNull RefEntity refEntity, @NotNull AnalysisScope scope, @NotNull InspectionManager manager, @NotNull final GlobalInspectionContext globalContext) {
    if (refEntity instanceof RefModule refModule){
      final Module module = refModule.getModule();
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      boolean onlyGeneratedSources = true;
      for (ContentEntry entry : moduleRootManager.getContentEntries()) {
        for (SourceFolder folder : entry.getSourceFolders()) {
          if (!JavaProjectRootsUtil.isForGeneratedSources(folder)) {
            onlyGeneratedSources = false;
            break;
          }
        }
      }
      if (onlyGeneratedSources) return null;

      final OrderEntry[] declaredDependencies = moduleRootManager.getOrderEntries();

      final List<CommonProblemDescriptor> descriptors = new ArrayList<>();
      final Set<Module> modules = refModule.getUserData(UnnecessaryModuleDependencyAnnotator.DEPENDENCIES);
      final List<Module> candidates = new ArrayList<>();
      for (final OrderEntry entry : declaredDependencies) {
        if (entry instanceof ModuleOrderEntry &&
            ((ModuleOrderEntry)entry).getScope() != DependencyScope.RUNTIME &&
            !((ModuleOrderEntry)entry).isExported()) {

          final Module dependency = ((ModuleOrderEntry)entry).getModule();
          if (dependency == null || modules != null && modules.remove(dependency)) {
            continue;
          }

          candidates.add(dependency);
        }
      }

      for (Module dependency : candidates) {
        if (modules != null) {
          HashSet<Module> outs = new HashSet<>();
          OrderEnumerator.orderEntries(dependency)
            .withoutSdk()
            .exportedOnly()
            .recursively()
            .forEachModule(outs::add);

          if (ContainerUtil.intersects(modules, outs)) continue;
        }

        descriptors.add(createDescriptor(scope, manager, module, dependency));
      }

      return descriptors.isEmpty() ? null : descriptors.toArray(CommonProblemDescriptor.EMPTY_ARRAY);
    }
    return null;
  }

  @Nullable
  @Override
  public RemoveModuleDependencyFix getQuickFix(String hint) {
    return new RemoveModuleDependencyFix(hint);
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.declaration.redundancy");
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return "UnnecessaryModuleDependencyInspection";
  }

  @Nullable
  @Override
  public String getHint(@NotNull QuickFix fix) {
    return fix instanceof RemoveModuleDependencyFix ? ((RemoveModuleDependencyFix)fix).myDependency : null;
  }

  private static CommonProblemDescriptor createDescriptor(AnalysisScope scope,
                                                          InspectionManager manager,
                                                          @NotNull Module module,
                                                          @NotNull Module dependency) {
    String dependencyName = dependency.getName();
    String moduleName = module.getName();
    if (scope.containsModule(dependency)) { //external references are rejected -> annotator doesn't provide any information on them -> false positives
      final String allContainsMessage = JavaAnalysisBundle.message("unnecessary.module.dependency.problem.descriptor", moduleName, dependencyName);
      return manager.createProblemDescriptor(allContainsMessage, module, new RemoveModuleDependencyFix(dependencyName));
    }
    else {
      String message = JavaAnalysisBundle.message("suspected.module.dependency.problem.descriptor", moduleName, dependencyName, scope.getDisplayName());
      return manager.createProblemDescriptor(message, module);
    }
  }

  public static class RemoveModuleDependencyFix implements QuickFix<ModuleProblemDescriptor> {

    private final String myDependency;

    public RemoveModuleDependencyFix(String dependency) {
      myDependency = dependency;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return JavaAnalysisBundle.message("remove.dependency");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ModuleProblemDescriptor descriptor) {
      final ModifiableRootModel model = ModuleRootManager.getInstance(descriptor.getModule()).getModifiableModel();
      for (OrderEntry entry : model.getOrderEntries()) {
        if (entry instanceof ModuleOrderEntry) {
          final String mDependency = ((ModuleOrderEntry)entry).getModuleName();
          if (Objects.equals(mDependency, myDependency)) {
            model.removeOrderEntry(entry);
            break;
          }
        }
      }
      model.commit();
    }
  }
}
