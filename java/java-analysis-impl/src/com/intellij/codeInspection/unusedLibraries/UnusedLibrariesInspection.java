/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.codeInspection.unusedLibraries;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefGraphAnnotator;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
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
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class UnusedLibrariesInspection extends GlobalInspectionTool {
  private static final Logger LOG = Logger.getInstance(UnusedLibrariesInspection.class);

  public boolean IGNORE_LIBRARY_PARTS = true;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel("Don't report unused library roots inside used library", this, "IGNORE_LIBRARY_PARTS");
  }

  @Nullable
  @Override
  public RefGraphAnnotator getAnnotator(@NotNull RefManager refManager) {
    return new UnusedLibraryGraphAnnotator(refManager);
  }

  @Nullable
  @Override
  public CommonProblemDescriptor[] checkElement(@NotNull RefEntity refEntity,
                                                @NotNull AnalysisScope scope,
                                                @NotNull InspectionManager manager,
                                                @NotNull GlobalInspectionContext globalContext,
                                                @NotNull ProblemDescriptionsProcessor processor) {
    if (refEntity instanceof RefModule) {
      final RefModule refModule = (RefModule)refEntity;
      final Module module = refModule.getModule();
     
      VirtualFile[] givenRoots =
        OrderEnumerator.orderEntries(module).withoutSdk()
          .withoutModuleSourceEntries()
          .withoutDepModules()
          .classes()
          .usingCache().getRoots();

      if (givenRoots.length == 0) return null;

      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      final Set<VirtualFile> usedRoots = refModule.getUserData(UnusedLibraryGraphAnnotator.USED_LIBRARY_ROOTS);

      if (usedRoots != null) {
        appendUsedRootDependencies(usedRoots, givenRoots);
      }

      final List<CommonProblemDescriptor> result = new ArrayList<>();
      for (OrderEntry entry : moduleRootManager.getOrderEntries()) {
        if (entry instanceof LibraryOrderEntry && 
            !((LibraryOrderEntry)entry).isExported() && 
            ((LibraryOrderEntry)entry).getScope() != DependencyScope.RUNTIME) {
          final Set<VirtualFile> files = new HashSet<>(Arrays.asList(((LibraryOrderEntry)entry).getRootFiles(OrderRootType.CLASSES)));
          boolean allRootsUnused = usedRoots == null || !files.removeAll(usedRoots);
          if (allRootsUnused) {
            String message = InspectionsBundle.message("unused.library.problem.descriptor", entry.getPresentableName());
            result.add(manager.createProblemDescriptor(message, module, new RemoveUnusedLibrary(entry.getPresentableName(), null)));
          }
          else if (!files.isEmpty() && !IGNORE_LIBRARY_PARTS) {
            final String unusedLibraryRoots = StringUtil.join(files, file -> file.getPresentableName(), ",");
            String message =
              InspectionsBundle.message("unused.library.roots.problem.descriptor", unusedLibraryRoots, entry.getPresentableName());
            CommonProblemDescriptor descriptor =
              ((LibraryOrderEntry)entry).isModuleLevel() 
              ? manager.createProblemDescriptor(message, module, new RemoveUnusedLibrary(entry.getPresentableName(), files))
              : manager.createProblemDescriptor(message);
            result.add(descriptor);
          }
        }
      }

      return result.isEmpty() ? null : result.toArray(CommonProblemDescriptor.EMPTY_ARRAY);
    }
    return null;
  }

  private static void appendUsedRootDependencies(@NotNull Set<VirtualFile> usedRoots,
                                                 @NotNull VirtualFile[] givenRoots) {
    //classes per root
    Map<VirtualFile, Set<String>> fromClasses = new THashMap<>();
    //classes uses in root, ignoring self & jdk
    Map<VirtualFile, Set<String>> toClasses = new THashMap<>();
    collectClassesPerRoots(givenRoots, fromClasses, toClasses);

    Graph<VirtualFile> graph = GraphGenerator.generate(new InboundSemiGraph<VirtualFile>() {
      @Override
      public Collection<VirtualFile> getNodes() {
        return Arrays.asList(givenRoots);
      }

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
      Set<String> fromClassNames = new THashSet<>();
      Set<String> toClassNames = new THashSet<>();

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
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("unused.library.display.name");
  }

  @Override
  @NonNls
  @NotNull
  public String getShortName() {
    return "UnusedLibrary";
  }

  @Nullable
  @Override
  public QuickFix getQuickFix(String hint) {
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
    private final Set<VirtualFile> myFiles;
    private String myLibraryName;

    public RemoveUnusedLibrary(String libraryName, final Set<VirtualFile> files) {
      myLibraryName = libraryName;
      myFiles = files;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return myFiles == null ? InspectionsBundle.message("detach.library.quickfix.name") : InspectionsBundle.message("detach.library.roots.quickfix.name");
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

    public UnusedLibraryGraphAnnotator(RefManager manager) {
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
