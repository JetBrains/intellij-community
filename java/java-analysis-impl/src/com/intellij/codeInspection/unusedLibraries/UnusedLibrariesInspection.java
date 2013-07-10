/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 18-Apr-2007
 */
package com.intellij.codeInspection.unusedLibraries;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.JobDescriptor;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.BackwardDependenciesBuilder;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.PackageSetFactory;
import com.intellij.psi.search.scope.packageSet.ParsingException;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class UnusedLibrariesInspection extends GlobalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#" + UnusedLibrariesInspection.class.getName());
  private final JobDescriptor BACKWARD_ANALYSIS = new JobDescriptor(InspectionsBundle.message("unused.library.backward.analysis.job.description"));

  @Nullable
  @Override
  public JobDescriptor[] getAdditionalJobs() {
    return new JobDescriptor[]{BACKWARD_ANALYSIS};
  }

  @Override
  public void runInspection(@NotNull AnalysisScope scope,
                            @NotNull InspectionManager manager,
                            @NotNull final GlobalInspectionContext globalContext,
                            @NotNull ProblemDescriptionsProcessor problemProcessor) {
    final Project project = manager.getProject();
    final ArrayList<VirtualFile> libraryRoots = new ArrayList<VirtualFile>();
    if (scope.getScopeType() == AnalysisScope.PROJECT) {
      ContainerUtil.addAll(libraryRoots, LibraryUtil.getLibraryRoots(project, false, false));
    }
    else {
      final Set<Module> modules = new HashSet<Module>();
      scope.accept(new PsiRecursiveElementVisitor() {
        @Override
        public void visitFile(PsiFile file) {
          if (!(file instanceof PsiCompiledElement)) {
            final VirtualFile virtualFile = file.getVirtualFile();
            if (virtualFile != null) {
              final Module module = ModuleUtilCore.findModuleForFile(virtualFile, project);
              if (module != null) {
                modules.add(module);
              }
            }
          }
        }
      });
      ContainerUtil.addAll(libraryRoots, LibraryUtil.getLibraryRoots(modules.toArray(new Module[modules.size()]), false, false));
    }
    if (libraryRoots.isEmpty()) {
      return;
    }

    GlobalSearchScope searchScope;
    try {
      @NonNls final String libsName = "libs";
      NamedScope libScope = new NamedScope(libsName, PackageSetFactory.getInstance().compile("lib:*..*"));
      searchScope = GlobalSearchScopesCore.filterScope(project, libScope);
    }
    catch (ParsingException e) {
      //can't be
      LOG.error(e);
      return;
    }
    final AnalysisScope analysisScope = new AnalysisScope(searchScope, project);
    analysisScope.setSearchInLibraries(true);
    final BackwardDependenciesBuilder builder = new BackwardDependenciesBuilder(project, analysisScope);

    final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();

    BACKWARD_ANALYSIS.setTotalAmount(builder.getTotalFileCount());
    ProgressIndicator progress = new AbstractProgressIndicatorBase() {
      @Override
      public void setFraction(final double fraction) {
        super.setFraction(fraction);
        int nextAmount = (int)(fraction * BACKWARD_ANALYSIS.getTotalAmount());
        if (nextAmount > BACKWARD_ANALYSIS.getDoneAmount() && nextAmount < BACKWARD_ANALYSIS.getTotalAmount()) {
          BACKWARD_ANALYSIS.setDoneAmount(nextAmount);
          globalContext.incrementJobDoneAmount(BACKWARD_ANALYSIS, getText2());
        }
      }

      @Override
      public boolean isCanceled() {
        return progressIndicator != null && progressIndicator.isCanceled() || super.isCanceled();
      }
    };
    ProgressManager.getInstance().executeProcessUnderProgress(new Runnable() {
      @Override
      public void run() {
        builder.analyze();
      }
    }, progress);
    BACKWARD_ANALYSIS.setDoneAmount(BACKWARD_ANALYSIS.getTotalAmount());
    final Map<PsiFile, Set<PsiFile>> dependencies = builder.getDependencies();
    for (PsiFile file : dependencies.keySet()) {
      final VirtualFile virtualFile = file.getVirtualFile();
      LOG.assertTrue(virtualFile != null);
      for (Iterator<VirtualFile> i = libraryRoots.iterator(); i.hasNext();) {
        if (VfsUtilCore.isAncestor(i.next(), virtualFile, false)) {
          i.remove();
        }
      }
    }
    if (libraryRoots.isEmpty()) {
      return;
    }
    ProjectFileIndex projectIndex = ProjectRootManager.getInstance(project).getFileIndex();
    Map<OrderEntry, Set<VirtualFile>> unusedLibs = new HashMap<OrderEntry, Set<VirtualFile>>();
    for (VirtualFile libraryRoot : libraryRoots) {
      final List<OrderEntry> orderEntries = projectIndex.getOrderEntriesForFile(libraryRoot);
      for (OrderEntry orderEntry : orderEntries) {
        Set<VirtualFile> files = unusedLibs.get(orderEntry);
        if (files == null) {
          files = new HashSet<VirtualFile>();
          unusedLibs.put(orderEntry, files);
        }
        files.add(libraryRoot);
      }
    }
    final RefManager refManager = globalContext.getRefManager();
    for (OrderEntry orderEntry : unusedLibs.keySet()) {
      if (!(orderEntry instanceof LibraryOrderEntry)) continue;
      final RefModule refModule = refManager.getRefModule(orderEntry.getOwnerModule());
      final Set<VirtualFile> files = unusedLibs.get(orderEntry);
      final VirtualFile[] roots = ((LibraryOrderEntry)orderEntry).getRootFiles(OrderRootType.CLASSES);
      if (files.size() < roots.length) {
        final String unusedLibraryRoots = StringUtil.join(files, new Function<VirtualFile, String>() {
            @Override
            public String fun(final VirtualFile file) {
              return file.getPresentableName();
            }
          }, ",");
        String message =
          InspectionsBundle.message("unused.library.roots.problem.descriptor", unusedLibraryRoots, orderEntry.getPresentableName());
        problemProcessor.addProblemElement(refModule,
                                           manager.createProblemDescriptor(message, new RemoveUnusedLibrary(refModule, orderEntry, files)));
      }
      else {
        String message = InspectionsBundle.message("unused.library.problem.descriptor", orderEntry.getPresentableName());
        problemProcessor.addProblemElement(refModule,
                                           manager.createProblemDescriptor(message, new RemoveUnusedLibrary(refModule, orderEntry, null)));
      }
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

  private static class RemoveUnusedLibrary implements QuickFix {
    private final RefModule myRefModule;
    private final OrderEntry myOrderEntry;
    private final Set<VirtualFile> myFiles;

    public RemoveUnusedLibrary(final RefModule refModule, final OrderEntry orderEntry, final Set<VirtualFile> files) {
      myRefModule = refModule;
      myOrderEntry = orderEntry;
      myFiles = files;
    }

    @Override
    @NotNull
    public String getName() {
      return myFiles == null ? InspectionsBundle.message("detach.library.quickfix.name") : InspectionsBundle.message("detach.library.roots.quickfix.name");
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull final CommonProblemDescriptor descriptor) {
      final Module module = myRefModule.getModule();

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
          for (OrderEntry entry : model.getOrderEntries()) {
            if (entry instanceof LibraryOrderEntry && Comparing.strEqual(entry.getPresentableName(), myOrderEntry.getPresentableName())) {
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
      });
    }
  }
}
