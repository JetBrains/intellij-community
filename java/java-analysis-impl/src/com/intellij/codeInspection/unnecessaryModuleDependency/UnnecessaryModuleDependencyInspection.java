package com.intellij.codeInspection.unnecessaryModuleDependency;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefGraphAnnotator;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.ArrayUtil;
import com.intellij.util.graph.Graph;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * User: anna
 * Date: 09-Jan-2006
 */
public class UnnecessaryModuleDependencyInspection extends GlobalInspectionTool {

  private SoftReference<Graph<Module>> myGraph = new SoftReference<>(null);

  @Override
  public RefGraphAnnotator getAnnotator(@NotNull final RefManager refManager) {
    return new UnnecessaryModuleDependencyAnnotator(refManager);
  }

  @Override
  public CommonProblemDescriptor[] checkElement(@NotNull RefEntity refEntity, @NotNull AnalysisScope scope, @NotNull InspectionManager manager, @NotNull final GlobalInspectionContext globalContext) {
    if (refEntity instanceof RefModule){
      final RefModule refModule = (RefModule)refEntity;
      final Module module = refModule.getModule();
      if (module.isDisposed() || !scope.containsModule(module)) return CommonProblemDescriptor.EMPTY_ARRAY;
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      final OrderEntry[] declaredDependencies = moduleRootManager.getOrderEntries();
      final Module[] declaredModuleDependencies = moduleRootManager.getDependencies();

      List<CommonProblemDescriptor> descriptors = new ArrayList<>();
      final Set<Module> modules = refModule.getUserData(UnnecessaryModuleDependencyAnnotator.DEPENDENCIES);
      Graph<Module> graph = myGraph.get();
      if (graph == null) {
        graph = ModuleManager.getInstance(globalContext.getProject()).moduleGraph();
        myGraph = new SoftReference<>(graph);
      }

      final RefManager refManager = globalContext.getRefManager();
      for (final OrderEntry entry : declaredDependencies) {
        if (entry instanceof ModuleOrderEntry) {
          final Module dependency = ((ModuleOrderEntry)entry).getModule();
          if (dependency != null) {
            if (modules == null || !modules.contains(dependency)) {
              List<String> dependenciesThroughExported = null;
              if (((ModuleOrderEntry)entry).isExported()) {
                final Iterator<Module> iterator = graph.getOut(module);
                while (iterator.hasNext()) {
                  final Module dep = iterator.next();
                  final RefModule depRefModule = refManager.getRefModule(dep);
                  if (depRefModule != null) {
                    final Set<Module> neededModules = depRefModule.getUserData(UnnecessaryModuleDependencyAnnotator.DEPENDENCIES);
                    if (neededModules != null && neededModules.contains(dependency)) {
                      if (dependenciesThroughExported == null) {
                        dependenciesThroughExported = new ArrayList<>();
                      }
                      dependenciesThroughExported.add(dep.getName());
                    }
                  }
                }
              }
              if (modules != null) {
                List<String> transitiveDependencies = new ArrayList<>();
                final OrderEntry[] dependenciesOfDependencies = ModuleRootManager.getInstance(dependency).getOrderEntries();
                for (OrderEntry secondDependency : dependenciesOfDependencies) {
                  if (secondDependency instanceof ModuleOrderEntry && ((ModuleOrderEntry)secondDependency).isExported()) {
                    final Module mod = ((ModuleOrderEntry)secondDependency).getModule();
                    if (mod != null && modules.contains(mod) && ArrayUtil.find(declaredModuleDependencies, mod) < 0) {
                      transitiveDependencies.add(mod.getName());
                    }
                  }
                }
                if (!transitiveDependencies.isEmpty()) {
                  final String exported = StringUtil.join(transitiveDependencies, ", ");
                  descriptors.add(manager.createProblemDescriptor(InspectionsBundle.message("unnecessary.module.dependency.exported.problem.descriptor1", module.getName(), dependency.getName(), exported)));
                  continue;
                }
              }

              descriptors.add(createDescriptor(scope, manager, module, dependency, dependenciesThroughExported));
            }
          }
        }
      }
      return descriptors.isEmpty() ? null : descriptors.toArray(new CommonProblemDescriptor[descriptors.size()]);
    }
    return null;
  }

  private static CommonProblemDescriptor createDescriptor(AnalysisScope scope,
                                                          InspectionManager manager,
                                                          Module module,
                                                          Module dependency, 
                                                          List<String> exportedDependencies) {
    if (exportedDependencies != null) {
      final String exported = StringUtil.join(exportedDependencies, ", ");
      return manager.createProblemDescriptor(InspectionsBundle.message("unnecessary.module.dependency.exported.problem.descriptor", module.getName(), dependency.getName(), exported));
    }

    if (scope.containsModule(dependency)) { //external references are rejected -> annotator doesn't provide any information on them -> false positives
      final String allContainsMessage = InspectionsBundle.message("unnecessary.module.dependency.problem.descriptor", module.getName(), dependency.getName());
      return manager.createProblemDescriptor(allContainsMessage, new RemoveModuleDependencyFix(module, dependency));
    } else {
      String message = InspectionsBundle.message("suspected.module.dependency.problem.descriptor", module.getName(), dependency.getName(), scope.getDisplayName());
      return manager.createProblemDescriptor(message);
    }
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("unnecessary.module.dependency.display.name");
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return "UnnecessaryModuleDependencyInspection";
  }

  public static class RemoveModuleDependencyFix implements QuickFix {
    private final Module myModule;
    private final Module myDependency;

    public RemoveModuleDependencyFix(Module module, Module dependency) {
      myModule = module;
      myDependency = dependency;
    }

    @Override
    @NotNull
    public String getName() {
      return "Remove dependency";
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull CommonProblemDescriptor descriptor) {
      final ModifiableRootModel model = ModuleRootManager.getInstance(myModule).getModifiableModel();
      for (OrderEntry entry : model.getOrderEntries()) {
        if (entry instanceof ModuleOrderEntry) {
          final Module mDependency = ((ModuleOrderEntry)entry).getModule();
          if (Comparing.equal(mDependency, myDependency)) {
            model.removeOrderEntry(entry);
            break;
          }
        }
      }
      model.commit();
    }
  }
}
