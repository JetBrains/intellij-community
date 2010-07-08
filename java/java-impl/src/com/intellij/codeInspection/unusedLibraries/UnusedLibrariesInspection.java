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
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.QuickFix;
import com.intellij.codeInspection.ex.DescriptorProviderInspection;
import com.intellij.codeInspection.ex.JobDescriptor;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.BackwardDependenciesBuilder;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.PackageSetFactory;
import com.intellij.psi.search.scope.packageSet.ParsingException;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class UnusedLibrariesInspection extends DescriptorProviderInspection {
  private static final Logger LOG = Logger.getInstance("#" + UnusedLibrariesInspection.class.getName());
  private static final JobDescriptor BACKWARD_ANALYSIS = new JobDescriptor(InspectionsBundle.message("unused.library.backward.analysis.job.description"));

  public void runInspection(final AnalysisScope scope, final InspectionManager manager) {
    final Project project = getContext().getProject();
    final ArrayList<VirtualFile> libraryRoots = new ArrayList<VirtualFile>();
    if (scope.getScopeType() == AnalysisScope.PROJECT) {
      ContainerUtil.addAll(libraryRoots, LibraryUtil.getLibraryRoots(project, false, false));
    } else {
      final Set<Module> modules = new HashSet<Module>();
      scope.accept(new PsiRecursiveElementVisitor() {
        @Override
        public void visitFile(PsiFile file) {
          if (!(file instanceof PsiCompiledElement)) {
            final VirtualFile virtualFile = file.getVirtualFile();
            if (virtualFile != null) {
              final Module module = ModuleUtil.findModuleForFile(virtualFile, project);
              if (module != null) {
                modules.add(module);
              }
            }
          }
        }
      });
      ContainerUtil.addAll(libraryRoots, LibraryUtil.getLibraryRoots(modules.toArray(new Module[modules.size()]), false, false));
    }
    GlobalSearchScope searchScope;
    try {
      @NonNls final String libsName = "libs";
      searchScope = GlobalSearchScope.filterScope(project, new NamedScope(libsName, PackageSetFactory.getInstance().compile("lib:*..*")));
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
    ((ProgressManagerImpl)ProgressManager.getInstance()).executeProcessUnderProgress(new Runnable(){
      public void run() {
        builder.analyze();
      }
    }, new ProgressIndicatorBase() {
      public void setFraction(final double fraction) {
        super.setFraction(fraction);
        BACKWARD_ANALYSIS.setDoneAmount(((int)fraction * BACKWARD_ANALYSIS.getTotalAmount()));
        getContext().incrementJobDoneAmount(BACKWARD_ANALYSIS, getText2());
      }

      public boolean isCanceled() {
        return progressIndicator != null && progressIndicator.isCanceled() || super.isCanceled();
      }
    });
    final Map<PsiFile, Set<PsiFile>> dependencies = builder.getDependencies();
    for (PsiFile file : dependencies.keySet()) {
      final VirtualFile virtualFile = file.getVirtualFile();
      LOG.assertTrue(virtualFile != null);
      for (Iterator<VirtualFile> i = libraryRoots.iterator(); i.hasNext();) {
        if (VfsUtil.isAncestor(i.next(), virtualFile, false)) {
          i.remove();
        }
      }
    }
    if (libraryRoots.size() > 0) {
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
      final RefManager refManager = getRefManager();
      for (OrderEntry orderEntry : unusedLibs.keySet()) {
        if (orderEntry instanceof LibraryOrderEntry) {
          final RefModule refModule = refManager.getRefModule(orderEntry.getOwnerModule());
          final Set<VirtualFile> files = unusedLibs.get(orderEntry);
          final VirtualFile[] roots = ((LibraryOrderEntry)orderEntry).getRootFiles(OrderRootType.CLASSES);
          if (files.size() < roots.length) {
            final String unusedLibraryRoots = StringUtil.join(files, new Function<VirtualFile, String>() {
              public String fun(final VirtualFile file) {
                return file.getPresentableName();
              }
            }, ",");
            addProblemElement(refModule, manager.createProblemDescriptor(InspectionsBundle.message(
              "unused.library.roots.problem.descriptor", unusedLibraryRoots, orderEntry.getPresentableName()), new RemoveUnusedLibrary(refModule, orderEntry, files)));
          } else {
            addProblemElement(refModule, manager.createProblemDescriptor(InspectionsBundle.message("unused.library.problem.descriptor",
                                                                                                       orderEntry.getPresentableName()), new RemoveUnusedLibrary(refModule, orderEntry, null)));
          }
        }
      }
    }
  }

  public boolean isGraphNeeded() {
    return false;
  }

  @NotNull
  public JobDescriptor[] getJobDescriptors() {
    return new JobDescriptor[] {BACKWARD_ANALYSIS};
  }

  public boolean isEnabledByDefault() {
    return false;
  }

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("unused.library.display.name");
  }

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

    @NotNull
    public String getName() {
      return myFiles == null ? InspectionsBundle.message("detach.library.quickfix.name") : InspectionsBundle.message("detach.library.roots.quickfix.name");
    }

    @NotNull
    public String getFamilyName() {
      return getName();
    }

    public void applyFix(@NotNull final Project project, @NotNull final CommonProblemDescriptor descriptor) {
      final Module module = myRefModule.getModule();

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
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
