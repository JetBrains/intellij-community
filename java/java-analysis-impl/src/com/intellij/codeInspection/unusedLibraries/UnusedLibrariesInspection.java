// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.unusedLibraries;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.reference.RefGraphAnnotator;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.java.analysis.impl.bytecode.AbstractDependencyVisitor;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphAlgorithms;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.graph.InboundSemiGraph;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class UnusedLibrariesInspection extends GlobalInspectionTool {
  private static final Logger LOG = Logger.getInstance(UnusedLibrariesInspection.class);

  public boolean IGNORE_LIBRARY_PARTS = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("IGNORE_LIBRARY_PARTS", JavaAnalysisBundle.message("don.t.report.unused.jars.inside.used.library")));
  }

  @Override
  public @NotNull RefGraphAnnotator getAnnotator(@NotNull RefManager refManager) {
    return new UnusedLibraryGraphAnnotator(refManager);
  }

  @Override
  public boolean isReadActionNeeded() {
    return false;
  }

  @Override
  public void runInspection(@NotNull AnalysisScope scope,
                            @NotNull InspectionManager manager,
                            @NotNull GlobalInspectionContext globalContext,
                            @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    RefManager refManager = globalContext.getRefManager();
    for (Module module : ModuleManager.getInstance(globalContext.getProject()).getModules()) {
      if (ReadAction.compute(() -> scope.containsModule(module))) {
        RefModule refModule = refManager.getRefModule(module);
        if (refModule != null) {
          CommonProblemDescriptor[] descriptors = getDescriptors(manager, refModule, module);
          if (descriptors != null) {
            problemDescriptionsProcessor.addProblemElement(refModule, descriptors);
          }
        }
      }
    }
  }

  private CommonProblemDescriptor @Nullable [] getDescriptors(@NotNull InspectionManager manager,
                                                              RefModule refModule,
                                                              Module module) {
    VirtualFile[] givenRoots =
      ReadAction.compute(() -> OrderEnumerator.orderEntries(module).withoutSdk()
        .withoutModuleSourceEntries()
        .withoutDepModules()
        .classes()
        .getRoots());

    if (givenRoots.length == 0) return null;

    final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    final Set<VirtualFile> usedRoots = refModule.getUserData(UnusedLibraryGraphAnnotator.USED_LIBRARY_ROOTS);

    if (usedRoots != null) {
      appendUsedRootDependencies(usedRoots, givenRoots);
    }

    return ReadAction.compute(() -> {
      final List<CommonProblemDescriptor> result = new ArrayList<>();
      for (OrderEntry entry : moduleRootManager.getOrderEntries()) {
        if (entry instanceof LibraryOrderEntry &&
            !((LibraryOrderEntry)entry).isExported() &&
            ((LibraryOrderEntry)entry).getScope() != DependencyScope.RUNTIME) {
          final Set<VirtualFile> files = ContainerUtil.newHashSet(((LibraryOrderEntry)entry).getRootFiles(OrderRootType.CLASSES));
          boolean allRootsUnused = usedRoots == null || !files.removeAll(usedRoots);
          if (allRootsUnused) {
            String message = JavaAnalysisBundle.message("unused.library.problem.descriptor", entry.getPresentableName());
            result.add(manager.createProblemDescriptor(message, module, new RemoveUnusedLibrary(entry.getPresentableName(), null)));
          }
          else if (!files.isEmpty() && !IGNORE_LIBRARY_PARTS) {
            final String unusedLibraryRoots = StringUtil.join(files, file -> file.getPresentableName(), ",");
            String message =
              JavaAnalysisBundle.message("unused.library.roots.problem.descriptor", unusedLibraryRoots, entry.getPresentableName());
            CommonProblemDescriptor descriptor =
              ((LibraryOrderEntry)entry).isModuleLevel()
              ? manager.createProblemDescriptor(message, module, new RemoveUnusedLibrary(entry.getPresentableName(), files))
              : manager.createProblemDescriptor(message);
            result.add(descriptor);
          }
        }
      }

      return result.isEmpty() ? null : result.toArray(CommonProblemDescriptor.EMPTY_ARRAY);
    });
  }

  private static void appendUsedRootDependencies(@NotNull Set<VirtualFile> usedRoots,
                                                 VirtualFile @NotNull [] givenRoots) {
    //classes per root
    Map<VirtualFile, Set<String>> fromClasses = new HashMap<>();
    //classes uses in root, ignoring self & jdk
    Map<VirtualFile, Set<String>> toClasses = new HashMap<>();
    collectClassesPerRoots(givenRoots, fromClasses, toClasses);

    Graph<VirtualFile> graph = GraphGenerator.generate(new InboundSemiGraph<>() {
      @NotNull
      @Override
      public Collection<VirtualFile> getNodes() {
        return Arrays.asList(givenRoots);
      }

      @NotNull
      @Override
      public Iterator<VirtualFile> getIn(VirtualFile n) {
        Set<String> classesInCurrentRoot = fromClasses.get(n);
        return toClasses.entrySet().stream()
          .filter(entry -> ContainerUtil.intersects(entry.getValue(), classesInCurrentRoot))
          .map(entry -> entry.getKey())
          .collect(Collectors.toSet()).iterator();
      }
    });

    GraphAlgorithms algorithms = GraphAlgorithms.getInstance();
    Set<VirtualFile> dependencies = new HashSet<>();
    for (VirtualFile root : usedRoots) {
      algorithms.collectOutsRecursively(graph, root, dependencies);
    }
    usedRoots.addAll(dependencies);
  }

  private static void collectClassesPerRoots(VirtualFile[] givenRoots,
                                             Map<VirtualFile, Set<String>> fromClasses,
                                             Map<VirtualFile, Set<String>> toClasses) {
    for (VirtualFile root : givenRoots) {
      Set<String> fromClassNames = new HashSet<>();
      Set<String> toClassNames = new HashSet<>();

      VfsUtilCore.iterateChildrenRecursively(root, null, fileOrDir -> {
        if (!fileOrDir.isDirectory() && fileOrDir.getName().endsWith(".class")) {
          AbstractDependencyVisitor visitor = new AbstractDependencyVisitor() {
            @Override
            protected void addClassName(String name) {
              if (!name.startsWith("java.") && !name.startsWith("javax.")) { //ignore jdk classes
                toClassNames.add(name);
              }
            }
          };
          try {
            visitor.processStream(fileOrDir.getInputStream());
            fromClassNames.add(visitor.getCurrentClassName());
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
        return true;
      });
      toClassNames.removeAll(fromClassNames);

      fromClasses.put(root, fromClassNames);
      toClasses.put(root, toClassNames);
    }
  }


  @Override
  public boolean isEnabledByDefault() {
    return false;
  }

  @Override
  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.declaration.redundancy");
  }

  @Override
  @NonNls
  @NotNull
  public String getShortName() {
    return "UnusedLibrary";
  }

  @Override
  public @NotNull QuickFix<?> getQuickFix(String hint) {
    return new RemoveUnusedLibrary(hint, null);
  }

  @Nullable
  @Override
  public String getHint(@NotNull QuickFix fix) {
    if (fix instanceof RemoveUnusedLibrary && ((RemoveUnusedLibrary)fix).myFiles == null) {
      return ((RemoveUnusedLibrary)fix).myLibraryName;
    }
    return null;
  }

  private static class RemoveUnusedLibrary implements QuickFix<ModuleProblemDescriptor> {
    private final Set<? extends VirtualFile> myFiles;
    private final String myLibraryName;

    RemoveUnusedLibrary(String libraryName, final Set<? extends VirtualFile> files) {
      myLibraryName = libraryName;
      myFiles = files;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return myFiles == null ? JavaAnalysisBundle.message("detach.library.quickfix.name") : JavaAnalysisBundle.message("detach.library.roots.quickfix.name");
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull final ModuleProblemDescriptor descriptor) {
      final Module module = descriptor.getModule();

      final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
      for (OrderEntry entry : model.getOrderEntries()) {
        if (entry instanceof LibraryOrderEntry && Comparing.strEqual(entry.getPresentableName(), myLibraryName)) {
          if (myFiles == null) {
            model.removeOrderEntry(entry);
          }
          else {
            final Library library = ((LibraryOrderEntry)entry).getLibrary();
            if (library != null) {
              final Library.ModifiableModel modifiableModel = library.getModifiableModel();
              for (VirtualFile file : myFiles) {
                modifiableModel.removeRoot(file.getUrl(), OrderRootType.CLASSES);
              }
              modifiableModel.commit();
            }
          }
        }
      }
      model.commit();
    }
  }

  private static class UnusedLibraryGraphAnnotator extends RefGraphAnnotator {
    public static final Key<Set<VirtualFile>> USED_LIBRARY_ROOTS = Key.create("inspection.dependencies");
    private final ProjectFileIndex myFileIndex;
    private final RefManager myManager;

    UnusedLibraryGraphAnnotator(RefManager manager) {
      myManager = manager;
      myFileIndex = ProjectRootManager.getInstance(manager.getProject()).getFileIndex();
    }

    @Override
    public void onMarkReferenced(PsiElement what, PsiElement from, boolean referencedFromClassInitializer) {
      if (what != null && from != null){
        final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(what);
        final VirtualFile containingDir = virtualFile != null ? virtualFile.getParent() : null;
        if (containingDir != null) {
          final VirtualFile libraryClassRoot = myFileIndex.getClassRootForFile(containingDir);
          if (libraryClassRoot != null) {
            final Module fromModule = ModuleUtilCore.findModuleForPsiElement(from);
            if (fromModule != null){
              final RefModule refModule = myManager.getRefModule(fromModule);
              if (refModule != null) {
                Set<VirtualFile> usedRoots = refModule.getUserData(USED_LIBRARY_ROOTS);
                if (usedRoots == null){
                  usedRoots = new HashSet<>();
                  refModule.putUserData(USED_LIBRARY_ROOTS, usedRoots);
                }
                usedRoots.add(libraryClassRoot);
              }
            }
          }
        }
      }
    }
  }
}
