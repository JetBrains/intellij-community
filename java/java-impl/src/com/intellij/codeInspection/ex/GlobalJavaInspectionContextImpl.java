// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.ex;

import com.intellij.CommonBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ui.InspectionToolPresentation;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
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
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastContextKt;

import java.util.*;
import java.util.stream.Collectors;

public class GlobalJavaInspectionContextImpl extends GlobalJavaInspectionContext {
  private static final Logger LOG = Logger.getInstance(GlobalJavaInspectionContextImpl.class);

  private Map<SmartPsiElementPointer, List<DerivedMethodsProcessor>> myDerivedMethodsRequests;
  private Map<SmartPsiElementPointer, List<DerivedClassesProcessor>> myDerivedClassesRequests;
  private Map<SmartPsiElementPointer, List<UsagesProcessor>> myMethodUsagesRequests;
  private Map<SmartPsiElementPointer, List<UsagesProcessor>> myFieldUsagesRequests;
  private Map<SmartPsiElementPointer, List<UsagesProcessor>> myClassUsagesRequests;
  private Map<SmartPsiElementPointer, List<Runnable>> myQNameUsagesRequests;


  @Override
  public void enqueueClassUsagesProcessor(RefClass refClass, UsagesProcessor p) {
    if (myClassUsagesRequests == null) myClassUsagesRequests = new THashMap<>();
    enqueueRequestImpl(refClass, myClassUsagesRequests, p);

  }
  @Override
  public void enqueueDerivedClassesProcessor(RefClass refClass, DerivedClassesProcessor p) {
    if (myDerivedClassesRequests == null) myDerivedClassesRequests = new THashMap<>();
    enqueueRequestImpl(refClass, myDerivedClassesRequests, p);
  }

  @Override
  public void enqueueDerivedMethodsProcessor(RefMethod refMethod, DerivedMethodsProcessor p) {
    if (refMethod.isConstructor() || refMethod.isStatic()) return;
    if (myDerivedMethodsRequests == null) myDerivedMethodsRequests = new THashMap<>();
    enqueueRequestImpl(refMethod, myDerivedMethodsRequests, p);
  }

  @Override
  public void enqueueFieldUsagesProcessor(RefField refField, UsagesProcessor p) {
    if (myFieldUsagesRequests == null) myFieldUsagesRequests = new THashMap<>();
    enqueueRequestImpl(refField, myFieldUsagesRequests, p);
  }

  @Override
  public void enqueueMethodUsagesProcessor(RefMethod refMethod, UsagesProcessor p) {
    if (myMethodUsagesRequests == null) myMethodUsagesRequests = new THashMap<>();
    enqueueRequestImpl(refMethod, myMethodUsagesRequests, p);
  }

  @Override
  public void enqueueQualifiedNameOccurrencesProcessor(RefClass refClass, Runnable c) {
    if (myQNameUsagesRequests == null) myQNameUsagesRequests = new THashMap<>();
    enqueueRequestImpl(refClass, myQNameUsagesRequests, c);
  }

  @Override
  public EntryPointsManager getEntryPointsManager(final RefManager manager) {
    return manager.getExtension(RefJavaManager.MANAGER).getEntryPointsManager();
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
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
        DumbService.getInstance(project).completeJustSubmittedTasks();
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
            if (library == null) {
              System.err.println(InspectionsBundle.message("offline.inspections.library.was.not.resolved",
                                                           libraryOrderEntry.getPresentableName(), module.getName()));
            }
            else {
              Set<String> detectedUrls =
                Arrays.stream(library.getFiles(OrderRootType.CLASSES)).map(file -> file.getUrl()).collect(Collectors.toSet());
              HashSet<String> declaredUrls = new HashSet<>(Arrays.asList(library.getUrls(OrderRootType.CLASSES)));
              declaredUrls.removeAll(detectedUrls);
              if (!declaredUrls.isEmpty()) {
                System.err.println(InspectionsBundle.message("offline.inspections.library.urls.were.not.resolved",
                                                             StringUtil.join(declaredUrls, ", "),
                                                             libraryOrderEntry.getPresentableName(), module.getName()));
              }
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

  private static <T> void enqueueRequestImpl(RefElement refElement, Map<SmartPsiElementPointer, List<T>> requestMap, T processor) {
    List<T> requests = requestMap.computeIfAbsent(refElement.getPointer(), __ -> new ArrayList<>());
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


  private void processSearchRequests(final GlobalInspectionContext context) {
    final RefManager refManager = context.getRefManager();
    final AnalysisScope scope = refManager.getScope();

    final SearchScope searchScope = new GlobalSearchScope(refManager.getProject()) {
      private final boolean processedReferences = Registry.is("batch.inspections.process.external.elements");

      @Override
      public boolean contains(@NotNull VirtualFile file) {
        if (scope != null && !scope.contains(file)) {
          return true;
        }
        //e.g. xml files were not included in the graph, so usages there should be processed as external
        boolean inGraph = processedReferences ? refManager.isInGraph(file) : file.getFileType() == StdFileTypes.JAVA;
        return !inGraph;
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
        final UClass uClass = ReadAction.compute(() -> UastContextKt.toUElement(dereferenceInReadAction(sortedID), UClass.class));
        if (uClass == null) continue;
        context.incrementJobDoneAmount(context.getStdJobDescriptors().FIND_EXTERNAL_USAGES, getClassPresentableName(uClass));

        final List<DerivedClassesProcessor> processors = myDerivedClassesRequests.get(sortedID);
        LOG.assertTrue(processors != null, uClass.getClass().getName());
        ClassInheritorsSearch.search(uClass.getJavaPsi(), searchScope, false).forEach(createMembersProcessor(processors, scope));
      }

      myDerivedClassesRequests = null;
    }

    if (myDerivedMethodsRequests != null) {
      final List<SmartPsiElementPointer> sortedIDs = getSortedIDs(myDerivedMethodsRequests);
      for (SmartPsiElementPointer sortedID : sortedIDs) {
        final UMethod uMethod = ReadAction.compute(() -> UastContextKt.toUElement(dereferenceInReadAction(sortedID), UMethod.class));
        if (uMethod == null) continue;
        final RefMethod refMethod = (RefMethod)refManager.getReference(uMethod.getSourcePsi());

        context.incrementJobDoneAmount(context.getStdJobDescriptors().FIND_EXTERNAL_USAGES, refManager.getQualifiedName(refMethod));

        final List<DerivedMethodsProcessor> processors = myDerivedMethodsRequests.get(sortedID);
        LOG.assertTrue(processors != null, uMethod.getClass().getName());
        OverridingMethodsSearch.search(uMethod.getJavaPsi(), searchScope, true).forEach(createMembersProcessor(processors, scope));
      }

      myDerivedMethodsRequests = null;
    }

    if (myFieldUsagesRequests != null) {
      final List<SmartPsiElementPointer> sortedIDs = getSortedIDs(myFieldUsagesRequests);
      for (SmartPsiElementPointer sortedID : sortedIDs) {
        final PsiElement field = dereferenceInReadAction(sortedID);
        if (field == null) continue;
        final List<UsagesProcessor> processors = myFieldUsagesRequests.get(sortedID);

        LOG.assertTrue(processors != null, field.getClass().getName());
        context.incrementJobDoneAmount(context.getStdJobDescriptors().FIND_EXTERNAL_USAGES, refManager.getQualifiedName(refManager.getReference(field)));

        ReferencesSearch.search(field, searchScope, false)
          .forEach(new PsiReferenceProcessorAdapter(createReferenceProcessor(processors, context)));
      }

      myFieldUsagesRequests = null;
    }

    if (myClassUsagesRequests != null) {
      final List<SmartPsiElementPointer> sortedIDs = getSortedIDs(myClassUsagesRequests);
      for (SmartPsiElementPointer sortedID : sortedIDs) {
        final PsiElement classDeclaration = dereferenceInReadAction(sortedID);
        if (classDeclaration == null) continue;
        final List<UsagesProcessor> processors = myClassUsagesRequests.get(sortedID);

        LOG.assertTrue(processors != null, classDeclaration.getClass().getName());
        UClass uClass = ReadAction.compute(() -> UastContextKt.toUElement(classDeclaration, UClass.class));
        context.incrementJobDoneAmount(context.getStdJobDescriptors().FIND_EXTERNAL_USAGES, getClassPresentableName(uClass));

        ReferencesSearch.search(classDeclaration, searchScope, false).forEach(new PsiReferenceProcessorAdapter(createReferenceProcessor(processors, context)));
      }

      myClassUsagesRequests = null;
    }

    if (myMethodUsagesRequests != null) {
      List<SmartPsiElementPointer> sortedIDs = getSortedIDs(myMethodUsagesRequests);
      for (SmartPsiElementPointer sortedID : sortedIDs) {
        final UMethod uMethod = ReadAction.compute(() -> UastContextKt.toUElement(dereferenceInReadAction(sortedID), UMethod.class));
        if (uMethod == null) continue;
        final List<UsagesProcessor> processors = myMethodUsagesRequests.get(sortedID);

        LOG.assertTrue(processors != null, uMethod.getClass().getName());
        context.incrementJobDoneAmount(context.getStdJobDescriptors().FIND_EXTERNAL_USAGES, ReadAction.compute(() -> uMethod.getName()));

        PsiMethod javaMethod = ReadAction.compute(() -> uMethod.getJavaPsi());
        if (javaMethod != null) {
          MethodReferencesSearch.search(javaMethod, searchScope, true).forEach(new PsiReferenceProcessorAdapter(createReferenceProcessor(processors, context)));
        }
      }

      myMethodUsagesRequests = null;
    }

    if (myQNameUsagesRequests != null) {
      PsiSearchHelper helper = PsiSearchHelper.getInstance(refManager.getProject());
      RefJavaManager javaManager = refManager.getExtension(RefJavaManager.MANAGER);
      List<SmartPsiElementPointer> sortedIDs = getSortedIDs(myQNameUsagesRequests);
      for (SmartPsiElementPointer id : sortedIDs) {
        final UClass uClass = ReadAction.compute(() -> UastContextKt.toUElement(dereferenceInReadAction(id), UClass.class));
        String qualifiedName = uClass != null ? ReadAction.compute(() -> uClass.getQualifiedName()) : null;
        if (qualifiedName != null) {
          List<Runnable> callbacks = myQNameUsagesRequests.get(id);
          final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(context.getProject());
          final PsiNonJavaFileReferenceProcessor processor = (file, startOffset, endOffset) -> {
            for (Runnable callback : callbacks) {
              callback.run();
            }
            return false;
          };

          final DelegatingGlobalSearchScope globalSearchScope = new DelegatingGlobalSearchScope(projectScope) {
            Set<FileType> fileTypes = javaManager.getLanguages().stream().map(l -> l.getAssociatedFileType()).collect(Collectors.toSet());

            @Override
            public boolean contains(@NotNull VirtualFile file) {
              return !fileTypes.contains(file.getFileType()) && super.contains(file);
            }
          };

          helper.processUsagesInNonJavaFiles(qualifiedName, processor, globalSearchScope);
        }
      }


      myQNameUsagesRequests = null;
    }
  }

  private static String getClassPresentableName(@NotNull UClass uClass) {
    return ReadAction.compute(() -> {
      final String qualifiedName = uClass.getQualifiedName();
      return qualifiedName != null ? qualifiedName : uClass.getName();
    });
  }

  private static PsiElement dereferenceInReadAction(final SmartPsiElementPointer sortedID) {
    return ReadAction.compute(() -> sortedID.getElement());
  }

  private static <Member extends PsiMember, P extends Processor<Member>> PsiElementProcessorAdapter<Member> createMembersProcessor(final List<P> processors,
                                                                                                                                   final AnalysisScope scope) {
    return new PsiElementProcessorAdapter<>(member -> {
      if (scope.contains(member)) return true;
      final List<P> processorsArrayed = new ArrayList<>(processors);
      for (P processor : processorsArrayed) {
        if (!processor.process(member)) {
          processors.remove(processor);
        }
      }
      return !processors.isEmpty();
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

  private static int getRequestListSize(Map<?,?> list) {
    return list == null ? 0 : list.size();
  }

  private static List<SmartPsiElementPointer> getSortedIDs(final Map<SmartPsiElementPointer, ?> requests) {
    final List<SmartPsiElementPointer> result = new ArrayList<>();

    ApplicationManager.getApplication().runReadAction(() -> {
      for (SmartPsiElementPointer id : requests.keySet()) {
        if (id != null && id.getContainingFile() != null) {
          result.add(id);
        }
      }
      Collections.sort(result, (o1, o2) -> {
        PsiFile psiFile1 = o1.getContainingFile();
        LOG.assertTrue(psiFile1 != null);
        PsiFile psiFile2 = o2.getContainingFile();
        LOG.assertTrue(psiFile2 != null);
        return psiFile1.getName().compareTo(psiFile2.getName());
      });
    });

    return result;
  }

  private static PsiReferenceProcessor createReferenceProcessor(@NotNull final List<UsagesProcessor> processors,
                                                                final GlobalInspectionContext context) {
    return reference -> {
      AnalysisScope scope = context.getRefManager().getScope();
      if (scope != null && scope.contains(reference.getElement()) && reference.getElement().getLanguage() == JavaLanguage.INSTANCE ||
          PsiTreeUtil.getParentOfType(reference.getElement(), PsiDocComment.class) != null) {
        return true;
      }

      synchronized (processors) {
        UsagesProcessor[] processorsArrayed = processors.toArray(new UsagesProcessor[0]);
        for (UsagesProcessor processor : processorsArrayed) {
          if (!processor.process(reference)) {
            processors.remove(processor);
          }
        }
      }

      return !processors.isEmpty();
    };
  }

  @Override
  public void performPreRunActivities(@NotNull final List<Tools> globalTools,
                                      @NotNull final List<Tools> localTools,
                                      @NotNull final GlobalInspectionContext context) {
    if (globalTools.stream().anyMatch(tools -> {
      InspectionProfileEntry tool = tools.getTool().getTool();
      return tool instanceof GlobalInspectionTool && ((GlobalInspectionTool)tool).isGraphNeeded();
    })) {
      getEntryPointsManager(context.getRefManager()).resolveEntryPoints(context.getRefManager());
    }
    // UnusedDeclarationInspection should run first
    for (int i = 0; i < globalTools.size(); i++) {
      InspectionToolWrapper toolWrapper = globalTools.get(i).getTool();
      if (UnusedDeclarationInspectionBase.SHORT_NAME.equals(toolWrapper.getShortName())) {
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
      InspectionToolWrapper[] requestors = needRepeatSearchRequest.toArray(InspectionToolWrapper.EMPTY_ARRAY);
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
