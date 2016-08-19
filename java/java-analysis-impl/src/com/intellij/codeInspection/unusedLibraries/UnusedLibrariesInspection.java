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

/*
 * User: anna
 * Date: 18-Apr-2007
 */
package com.intellij.codeInspection.unusedLibraries;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefGraphAnnotator;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Function;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class UnusedLibrariesInspection extends GlobalInspectionTool {

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
      if (module.isDisposed() || !scope.containsModule(module)) return CommonProblemDescriptor.EMPTY_ARRAY;
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      final Set<VirtualFile> usedRoots = refModule.getUserData(UnusedLibraryGraphAnnotator.USED_LIBRARY_ROOTS);

      final List<CommonProblemDescriptor> result = new ArrayList<>();
      for (OrderEntry entry : moduleRootManager.getOrderEntries()) {
        if (entry instanceof LibraryOrderEntry && !((LibraryOrderEntry)entry).isExported()) {
          if (usedRoots == null) {
            String message = InspectionsBundle.message("unused.library.problem.descriptor", entry.getPresentableName());
            result.add(manager.createProblemDescriptor(message, new RemoveUnusedLibrary(refModule, entry, null)));
          } else {
            final Set<VirtualFile> files = new HashSet<>(Arrays.asList(((LibraryOrderEntry)entry).getRootFiles(OrderRootType.CLASSES)));
            files.removeAll(usedRoots);
            if (!files.isEmpty()) {
              final String unusedLibraryRoots = StringUtil.join(files, file -> file.getPresentableName(), ",");
              String message =
                InspectionsBundle.message("unused.library.roots.problem.descriptor", unusedLibraryRoots, entry.getPresentableName());
              processor.addProblemElement(refModule,
                                          manager.createProblemDescriptor(message, new RemoveUnusedLibrary(refModule, entry, files)));
            }
          }
        }
      }

      return result.isEmpty() ? null : result.toArray(new CommonProblemDescriptor[result.size()]);
    }
    return null;
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

      ApplicationManager.getApplication().runWriteAction(() -> {
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
      });
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
                Set<VirtualFile> modules = refModule.getUserData(USED_LIBRARY_ROOTS);
                if (modules == null){
                  modules = new HashSet<>();
                  refModule.putUserData(USED_LIBRARY_ROOTS, modules);
                }
                modules.add(libraryClassRoot);
              }
            }
          }
        }
      }
    }
  }
}
