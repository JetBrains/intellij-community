/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
 * Date: 19-Dec-2007
 */
package com.intellij.codeInspection.ex;

import com.intellij.CommonBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ui.InspectionToolPresentation;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class GlobalJavaInspectionContextImpl extends GlobalJavaInspectionContext {
  private static final Logger LOG = Logger.getInstance("#" + GlobalJavaInspectionContextImpl.class.getName());

  private THashMap<SmartPsiElementPointer, List<DerivedMethodsProcessor>> myDerivedMethodsRequests;
  private THashMap<SmartPsiElementPointer, List<DerivedClassesProcessor>> myDerivedClassesRequests;
  private THashMap<SmartPsiElementPointer, List<UsagesProcessor>> myMethodUsagesRequests;
  private THashMap<SmartPsiElementPointer, List<UsagesProcessor>> myFieldUsagesRequests;
  private THashMap<SmartPsiElementPointer, List<UsagesProcessor>> myClassUsagesRequests;


  @Override
  public void enqueueClassUsagesProcessor(RefClass refClass, UsagesProcessor p) {
    if (myClassUsagesRequests == null) myClassUsagesRequests = new THashMap<SmartPsiElementPointer, List<UsagesProcessor>>();
    enqueueRequestImpl(refClass, myClassUsagesRequests, p);

  }
  @Override
  public void enqueueDerivedClassesProcessor(RefClass refClass, DerivedClassesProcessor p) {
    if (myDerivedClassesRequests == null) myDerivedClassesRequests = new THashMap<SmartPsiElementPointer, List<DerivedClassesProcessor>>();
    enqueueRequestImpl(refClass, myDerivedClassesRequests, p);
  }

  @Override
  public void enqueueDerivedMethodsProcessor(RefMethod refMethod, DerivedMethodsProcessor p) {
    if (refMethod.isConstructor() || refMethod.isStatic()) return;
    if (myDerivedMethodsRequests == null) myDerivedMethodsRequests = new THashMap<SmartPsiElementPointer, List<DerivedMethodsProcessor>>();
    enqueueRequestImpl(refMethod, myDerivedMethodsRequests, p);
  }

  @Override
  public void enqueueFieldUsagesProcessor(RefField refField, UsagesProcessor p) {
    if (myFieldUsagesRequests == null) myFieldUsagesRequests = new THashMap<SmartPsiElementPointer, List<UsagesProcessor>>();
    enqueueRequestImpl(refField, myFieldUsagesRequests, p);
  }

  @Override
  public void enqueueMethodUsagesProcessor(RefMethod refMethod, UsagesProcessor p) {
    if (myMethodUsagesRequests == null) myMethodUsagesRequests = new THashMap<SmartPsiElementPointer, List<UsagesProcessor>>();
    enqueueRequestImpl(refMethod, myMethodUsagesRequests, p);
  }

  @Override
  public EntryPointsManager getEntryPointsManager(final RefManager manager) {
    return manager.getExtension(RefJavaManager.MANAGER).getEntryPointsManager();
  }

  @SuppressWarnings({"UseOfSystemOutOrSystemErr"})
  public static boolean isInspectionsEnabled(final boolean online, @NotNull Project project) {
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    if (online) {
      if (modules.length == 0) {
        Messages.showMessageDialog(project, InspectionsBundle.message("inspection.no.modules.error.message"),
                                   CommonBundle.message("title.error"), Messages.getErrorIcon());
        return false;
      }
      while (isBadSdk(project, modules)) {
        Messages.showMessageDialog(project, InspectionsBundle.message("inspection.no.jdk.error.message"),
                                   CommonBundle.message("title.error"), Messages.getErrorIcon());
        final Sdk projectJdk = ProjectSettingsService.getInstance(project).chooseAndSetSdk();
        if (projectJdk == null) return false;
      }
    }
    else {
      if (modules.length == 0) {
        System.err.println(InspectionsBundle.message("inspection.no.modules.error.message"));
        return false;
      }
      if (isBadSdk(project, modules)) {
        System.err.println(InspectionsBundle.message("inspection.no.jdk.error.message"));
        System.err.println(
          InspectionsBundle.message("offline.inspections.jdk.not.found", ProjectRootManager.getInstance(project).getProjectSdkName()));
        return false;
      }
      for (Module module : modules) {
        final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
        final OrderEntry[] entries = rootManager.getOrderEntries();
        for (OrderEntry entry : entries) {
          if (entry instanceof JdkOrderEntry) {
            if (!ModuleType.get(module).isValidSdk(module, null)) {
              System.err.println(InspectionsBundle.message("offline.inspections.module.jdk.not.found", ((JdkOrderEntry)entry).getJdkName(),
                                                           module.getName()));
              return false;
            }
          }
          else if (entry instanceof LibraryOrderEntry) {
            final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)entry;
            final Library library = libraryOrderEntry.getLibrary();
            if (library == null || library.getFiles(OrderRootType.CLASSES).length < library.getUrls(OrderRootType.CLASSES).length) {
              System.err.println(InspectionsBundle.message("offline.inspections.library.was.not.resolved",
                                                           libraryOrderEntry.getPresentableName(), module.getName()));
            }
          }
        }
      }
    }
    return true;
  }

  private static boolean isBadSdk(final Project project, final Module[] modules) {
    boolean anyModuleAcceptsSdk = false;
    boolean anyModuleUsesProjectSdk = false;
    Sdk projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
    for (Module module : modules) {
      if (ModuleRootManager.getInstance(module).isSdkInherited()) {
        anyModuleUsesProjectSdk = true;
        if (ModuleType.get(module).isValidSdk(module, projectSdk)) {
          anyModuleAcceptsSdk = true;
        }
      }
    }
    return anyModuleUsesProjectSdk && !anyModuleAcceptsSdk;
  }

  private static <T extends Processor> void enqueueRequestImpl(RefElement refElement, Map<SmartPsiElementPointer, List<T>> requestMap, T processor) {
    List<T> requests = requestMap.get(refElement.getPointer());
    if (requests == null) {
      requests = new ArrayList<T>();
      requestMap.put(refElement.getPointer(), requests);
    }
    requests.add(processor);
  }

  @Override
  public void cleanup() {
    myDerivedMethodsRequests = null;
    myDerivedClassesRequests = null;
    myMethodUsagesRequests = null;
    myFieldUsagesRequests = null;
    myClassUsagesRequests = null;
  }


  public void processSearchRequests(final GlobalInspectionContext context) {
    final RefManager refManager = context.getRefManager();
    final AnalysisScope scope = refManager.getScope();

    final SearchScope searchScope = new GlobalSearchScope(refManager.getProject()) {
      @Override
      public boolean contains(@NotNull VirtualFile file) {
        return !scope.contains(file) || file.getFileType() != StdFileTypes.JAVA;
      }

      @Override
      public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
        return 0;
      }

      @Override
      public boolean isSearchInModuleContent(@NotNull Module aModule) {
        return true;
      }

      @Override
      public boolean isSearchInLibraries() {
        return false;
      }
    };

    if (myDerivedClassesRequests != null) {
      final List<SmartPsiElementPointer> sortedIDs = getSortedIDs(myDerivedClassesRequests);
      for (SmartPsiElementPointer sortedID : sortedIDs) {
        final PsiClass psiClass = (PsiClass)dereferenceInReadAction(sortedID);
        if (psiClass == null) continue;
        context.incrementJobDoneAmount(context.getStdJobDescriptors().FIND_EXTERNAL_USAGES, ApplicationManager.getApplication().runReadAction(
          new Computable<String>() {
            @Override
            public String compute() {
              return psiClass.getQualifiedName();
            }
          }
        ));

        final List<DerivedClassesProcessor> processors = myDerivedClassesRequests.get(sortedID);
        LOG.assertTrue(processors != null, psiClass.getClass().getName());
        ClassInheritorsSearch.search(psiClass, searchScope, false)
          .forEach(createMembersProcessor(processors, scope));
      }

      myDerivedClassesRequests = null;
    }

    if (myDerivedMethodsRequests != null) {
      final List<SmartPsiElementPointer> sortedIDs = getSortedIDs(myDerivedMethodsRequests);
      for (SmartPsiElementPointer sortedID : sortedIDs) {
        final PsiMethod psiMethod = (PsiMethod)dereferenceInReadAction(sortedID);
        if (psiMethod == null) continue;
        final RefMethod refMethod = (RefMethod)refManager.getReference(psiMethod);

        context.incrementJobDoneAmount(context.getStdJobDescriptors().FIND_EXTERNAL_USAGES, refManager.getQualifiedName(refMethod));

        final List<DerivedMethodsProcessor> processors = myDerivedMethodsRequests.get(sortedID);
        LOG.assertTrue(processors != null, psiMethod.getClass().getName());
        OverridingMethodsSearch.search(psiMethod, searchScope, true)
          .forEach(createMembersProcessor(processors, scope));
      }

      myDerivedMethodsRequests = null;
    }

    if (myFieldUsagesRequests != null) {
      final List<SmartPsiElementPointer> sortedIDs = getSortedIDs(myFieldUsagesRequests);
      for (SmartPsiElementPointer sortedID : sortedIDs) {
        final PsiField psiField = (PsiField)dereferenceInReadAction(sortedID);
        if (psiField == null) continue;
        final List<UsagesProcessor> processors = myFieldUsagesRequests.get(sortedID);

        LOG.assertTrue(processors != null, psiField.getClass().getName());
        context.incrementJobDoneAmount(context.getStdJobDescriptors().FIND_EXTERNAL_USAGES, refManager.getQualifiedName(refManager.getReference(psiField)));

        ReferencesSearch.search(psiField, searchScope, false)
          .forEach(new PsiReferenceProcessorAdapter(createReferenceProcessor(processors, context)));
      }

      myFieldUsagesRequests = null;
    }

    if (myClassUsagesRequests != null) {
      final List<SmartPsiElementPointer> sortedIDs = getSortedIDs(myClassUsagesRequests);
      for (SmartPsiElementPointer sortedID : sortedIDs) {
        final PsiClass psiClass = (PsiClass)dereferenceInReadAction(sortedID);
        if (psiClass == null) continue;
        final List<UsagesProcessor> processors = myClassUsagesRequests.get(sortedID);

        LOG.assertTrue(processors != null, psiClass.getClass().getName());
        context.incrementJobDoneAmount(context.getStdJobDescriptors().FIND_EXTERNAL_USAGES, ApplicationManager.getApplication().runReadAction(
          new Computable<String>() {
            @Override
            public String compute() {
              return psiClass.getQualifiedName();
            }
          }
        ));

        ReferencesSearch.search(psiClass, searchScope, false)
          .forEach(new PsiReferenceProcessorAdapter(createReferenceProcessor(processors, context)));
      }

      myClassUsagesRequests = null;
    }

    if (myMethodUsagesRequests != null) {
      List<SmartPsiElementPointer> sortedIDs = getSortedIDs(myMethodUsagesRequests);
      for (SmartPsiElementPointer sortedID : sortedIDs) {
        final PsiMethod psiMethod = (PsiMethod)dereferenceInReadAction(sortedID);
        if (psiMethod == null) continue;
        final List<UsagesProcessor> processors = myMethodUsagesRequests.get(sortedID);

        LOG.assertTrue(processors != null, psiMethod.getClass().getName());
        context.incrementJobDoneAmount(context.getStdJobDescriptors().FIND_EXTERNAL_USAGES, refManager.getQualifiedName(refManager.getReference(psiMethod)));

        MethodReferencesSearch.search(psiMethod, searchScope, true)
          .forEach(new PsiReferenceProcessorAdapter(createReferenceProcessor(processors, context)));
      }

      myMethodUsagesRequests = null;
    }
  }

  private static PsiElement dereferenceInReadAction(final SmartPsiElementPointer sortedID) {
    return ApplicationManager.getApplication().runReadAction(new Computable<PsiElement>() {
      @Override
      public PsiElement compute() {
        return sortedID.getElement();
      }
    });
  }

  private static <Member extends PsiMember, P extends Processor<Member>> PsiElementProcessorAdapter<Member> createMembersProcessor(final List<P> processors,
                                                                                                                                   final AnalysisScope scope) {
    return new PsiElementProcessorAdapter<Member>(new PsiElementProcessor<Member>() {
      @Override
      public boolean execute(@NotNull Member member) {
        if (scope.contains(member)) return true;
        final List<P> processorsArrayed = new ArrayList<P>(processors);
        for (P processor : processorsArrayed) {
          if (!processor.process(member)) {
            processors.remove(processor);
          }
        }
        return !processors.isEmpty();
      }
    });
  }

  private int getRequestCount() {
    int sum = 0;

    sum += getRequestListSize(myClassUsagesRequests);
    sum += getRequestListSize(myDerivedClassesRequests);
    sum += getRequestListSize(myDerivedMethodsRequests);
    sum += getRequestListSize(myFieldUsagesRequests);
    sum += getRequestListSize(myMethodUsagesRequests);

    return sum;
  }

  private static int getRequestListSize(THashMap list) {
    if (list == null) return 0;
    return list.size();
  }

  private static List<SmartPsiElementPointer> getSortedIDs(final Map<SmartPsiElementPointer, ?> requests) {
    final List<SmartPsiElementPointer> result = new ArrayList<SmartPsiElementPointer>();

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        for (SmartPsiElementPointer id : requests.keySet()) {
          if (id != null) {
            final PsiElement psi = id.getElement();
            if (psi != null) {
              result.add(id);
            }
          }
        }
        Collections.sort(result, new Comparator<SmartPsiElementPointer>() {
          @Override
          public int compare(final SmartPsiElementPointer o1, final SmartPsiElementPointer o2) {
            PsiElement p1 = o1.getElement();
            PsiElement p2 = o2.getElement();
            final PsiFile psiFile1 = p1 != null ? p1.getContainingFile() : null;
            LOG.assertTrue(psiFile1 != null);
            final PsiFile psiFile2 = p2 != null ? p2.getContainingFile() : null;
            LOG.assertTrue(psiFile2 != null);
            return psiFile1.getName().compareTo(psiFile2.getName());
          }
        });
      }
    });

    return result;
  }

  private static PsiReferenceProcessor createReferenceProcessor(@NotNull final List<UsagesProcessor> processors,
                                                                final GlobalInspectionContext context) {
    return new PsiReferenceProcessor() {
      @Override
      public boolean execute(PsiReference reference) {
        AnalysisScope scope = context.getRefManager().getScope();
        if (scope.contains(reference.getElement()) && reference.getElement().getLanguage() == StdLanguages.JAVA ||
            PsiTreeUtil.getParentOfType(reference.getElement(), PsiDocComment.class) != null) {
          return true;
        }

        synchronized (processors) {
          UsagesProcessor[] processorsArrayed = processors.toArray(new UsagesProcessor[processors.size()]);
          for (UsagesProcessor processor : processorsArrayed) {
            if (!processor.process(reference)) {
              processors.remove(processor);
            }
          }
        }

        return !processors.isEmpty();
      }
    };
  }

  @Override
  public void performPreRunActivities(@NotNull final List<Tools> globalTools,
                                      @NotNull final List<Tools> localTools,
                                      @NotNull final GlobalInspectionContext context) {
    getEntryPointsManager(context.getRefManager()).resolveEntryPoints(context.getRefManager());
    // UnusedDeclarationInspection should run first
    for (int i = 0; i < globalTools.size(); i++) {
      InspectionToolWrapper toolWrapper = globalTools.get(i).getTool();
      if (UnusedDeclarationInspection.SHORT_NAME.equals(toolWrapper.getShortName())) {
        Collections.swap(globalTools, i, 0);
        break;
      }
    }
  }



  @Override
  public void performPostRunActivities(@NotNull List<InspectionToolWrapper> needRepeatSearchRequest, @NotNull final GlobalInspectionContext context) {
    JobDescriptor progress = context.getStdJobDescriptors().FIND_EXTERNAL_USAGES;
    progress.setTotalAmount(getRequestCount());

    do {
      processSearchRequests(context);
      InspectionToolWrapper[] requestors = needRepeatSearchRequest.toArray(new InspectionToolWrapper[needRepeatSearchRequest.size()]);
      InspectionManager inspectionManager = InspectionManager.getInstance(context.getProject());
      for (InspectionToolWrapper toolWrapper : requestors) {
        boolean result = false;
        if (toolWrapper instanceof GlobalInspectionToolWrapper) {
          InspectionToolPresentation presentation = ((GlobalInspectionContextImpl)context).getPresentation(toolWrapper);
          result = ((GlobalInspectionToolWrapper)toolWrapper).getTool().queryExternalUsagesRequests(inspectionManager, context, presentation);
        }
        if (!result) {
          needRepeatSearchRequest.remove(toolWrapper);
        }
      }
      int oldSearchRequestCount = progress.getTotalAmount();
      int oldDoneAmount = progress.getDoneAmount();
      int totalAmount = oldSearchRequestCount + getRequestCount();
      progress.setTotalAmount(totalAmount);
      progress.setDoneAmount(oldDoneAmount);
    }
    while (!needRepeatSearchRequest.isEmpty());
  }

}
