// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.CommonBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ui.InspectionToolPresentation;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.SdkPopupFactory;
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
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastContextKt;

import java.util.*;
import java.util.stream.Collectors;

public final class GlobalJavaInspectionContextImpl extends GlobalJavaInspectionContext {
  private static final Logger LOG = Logger.getInstance(GlobalJavaInspectionContextImpl.class);

  private Map<SmartPsiElementPointer<?>, List<DerivedMethodsProcessor>> myDerivedMethodsRequests;
  private Map<SmartPsiElementPointer<?>, List<DerivedClassesProcessor>> myDerivedClassesRequests;
  private Map<SmartPsiElementPointer<?>, List<UsagesProcessor>> myMethodUsagesRequests;
  private Map<SmartPsiElementPointer<?>, List<UsagesProcessor>> myFieldUsagesRequests;
  private Map<SmartPsiElementPointer<?>, List<UsagesProcessor>> myClassUsagesRequests;
  private Map<SmartPsiElementPointer<?>, List<Runnable>> myQNameUsagesRequests;


  @Override
  public void enqueueClassUsagesProcessor(RefClass refClass, UsagesProcessor p) {
    LOG.assertTrue(!refClass.isAnonymous());
    if (myClassUsagesRequests == null) myClassUsagesRequests = new HashMap<>();
    enqueueRequestImpl(refClass, myClassUsagesRequests, p);

  }
  @Override
  public void enqueueDerivedClassesProcessor(RefClass refClass, DerivedClassesProcessor p) {
    LOG.assertTrue(!refClass.isAnonymous());
    if (myDerivedClassesRequests == null) myDerivedClassesRequests = new HashMap<>();
    enqueueRequestImpl(refClass, myDerivedClassesRequests, p);
  }

  @Override
  public void enqueueDerivedMethodsProcessor(RefMethod refMethod, DerivedMethodsProcessor p) {
    if (refMethod.isConstructor() || refMethod.isStatic()) return;
    if (myDerivedMethodsRequests == null) myDerivedMethodsRequests = new HashMap<>();
    enqueueRequestImpl(refMethod, myDerivedMethodsRequests, p);
  }

  @Override
  public void enqueueFieldUsagesProcessor(RefField refField, UsagesProcessor p) {
    if (myFieldUsagesRequests == null) myFieldUsagesRequests = new HashMap<>();
    enqueueRequestImpl(refField, myFieldUsagesRequests, p);
  }

  @Override
  public void enqueueMethodUsagesProcessor(RefMethod refMethod, UsagesProcessor p) {
    if (myMethodUsagesRequests == null) myMethodUsagesRequests = new HashMap<>();
    enqueueRequestImpl(refMethod, myMethodUsagesRequests, p);
  }

  @Override
  public void enqueueQualifiedNameOccurrencesProcessor(RefClass refClass, Runnable c) {
    if (myQNameUsagesRequests == null) myQNameUsagesRequests = new HashMap<>();
    enqueueRequestImpl(refClass, myQNameUsagesRequests, c);
  }

  @Override
  public EntryPointsManager getEntryPointsManager(RefManager manager) {
    return manager.getExtension(RefJavaManager.MANAGER).getEntryPointsManager();
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public static boolean isInspectionsEnabled(boolean online,
                                             @NotNull Project project,
                                             @NotNull Runnable rerunAction) {
    Module[] modules = ModuleManager.getInstance(project).getModules();
    if (online) {
      if (modules.length == 0) {
        Messages.showMessageDialog(project, JavaBundle.message("inspection.no.modules.error.message"),
                                   CommonBundle.getErrorTitle(), Messages.getErrorIcon());
        return false;
      }
      if (isBadSdk(project, modules)) {
        Messages.showMessageDialog(project, JavaBundle.message("inspection.no.jdk.error.message"),
                                   CommonBundle.getErrorTitle(), Messages.getErrorIcon());

        SdkPopupFactory
          .newBuilder()
          .withProject(project)
          .withSdkType(JavaSdk.getInstance())
          .updateProjectSdkFromSelection()
          .onSdkSelected(sdk -> {
            DumbService.getInstance(project).completeJustSubmittedTasks();
            rerunAction.run();
          })
          .buildPopup()
          .showInFocusCenter();

        return false;
      }
    }
    else {
      if (modules.length == 0) {
        System.err.println(JavaBundle.message("inspection.no.modules.error.message"));
        return false;
      }
      if (isBadSdk(project, modules)) {
        System.err.println(JavaBundle.message("inspection.no.jdk.error.message"));
        System.err.println(
          JavaBundle.message("offline.inspections.jdk.not.found", ProjectRootManager.getInstance(project).getProjectSdkName()));
        return false;
      }

      for (Module module : modules) {
        ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
        if (ModuleType.get(module) instanceof JavaModuleType && rootManager.getSourceRoots(true).length == 0) {
          LOG.info(JavaBundle.message("offline.inspections.no.source.roots", module.getName()));
        }
        OrderEntry[] entries = rootManager.getOrderEntries();
        for (OrderEntry entry : entries) {
          if (entry instanceof JdkOrderEntry) {
            if (!ModuleType.get(module).isValidSdk(module, null)) {
              System.err.println(InspectionsBundle.message("offline.inspections.module.jdk.not.found", ((JdkOrderEntry)entry).getJdkName(),
                                                           module.getName()));
              return false;
            }
          }
          else if (entry instanceof LibraryOrderEntry libraryOrderEntry) {
            Library library = libraryOrderEntry.getLibrary();
            if (library == null) {
              System.err.println(JavaBundle.message("offline.inspections.library.was.not.resolved",
                                                           libraryOrderEntry.getPresentableName(), module.getName()));
            }
            else {
              Set<String> detectedUrls =
                Arrays.stream(library.getFiles(OrderRootType.CLASSES)).map(VirtualFile::getUrl).collect(Collectors.toSet());
              Set<String> declaredUrls = ContainerUtil.newHashSet(library.getUrls(OrderRootType.CLASSES));
              declaredUrls.removeAll(detectedUrls);
              declaredUrls.removeIf(library::isJarDirectory);
              if (!declaredUrls.isEmpty()) {
                System.err.println(JavaBundle.message("offline.inspections.library.urls.were.not.resolved",
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

  private static boolean isBadSdk(Project project, Module[] modules) {
    return ProgressManager.getInstance().run(new Task.WithResult<Boolean, RuntimeException>(project,
                                                                                            JavaBundle.message("dialog.title.check.configuration"), true) {
      @Override
      protected Boolean compute(@NotNull ProgressIndicator indicator) throws RuntimeException {
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
    });
    
  }

  private static <T> void enqueueRequestImpl(RefElement refElement, Map<SmartPsiElementPointer<?>, List<T>> requestMap, T processor) {
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


  private void processSearchRequests(@NotNull GlobalInspectionContext context) {
    RefManager refManager = context.getRefManager();
    AnalysisScope scope = refManager.getScope();

    SearchScope searchScope = new GlobalSearchScope(refManager.getProject()) {
      private final boolean processedReferences = Registry.is("batch.inspections.process.external.elements");

      @Override
      public boolean contains(@NotNull VirtualFile file) {
        if (scope != null && !scope.contains(file)) {
          return true;
        }
        //e.g. xml files were not included in the graph, so usages there should be processed as external
        boolean inGraph = processedReferences ? refManager.isInGraph(file) : FileTypeRegistry.getInstance().isFileOfType(file, JavaFileType.INSTANCE);
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
      List<SmartPsiElementPointer<?>> sortedIDs = getSortedIDs(myDerivedClassesRequests);
      for (SmartPsiElementPointer<?> sortedID : sortedIDs) {
        UClass uClass = ReadAction.compute(() -> UastContextKt.toUElement(dereferenceInReadAction(sortedID), UClass.class));
        if (uClass == null) continue;
        context.incrementJobDoneAmount(context.getStdJobDescriptors().FIND_EXTERNAL_USAGES, getClassPresentableName(uClass));

        List<DerivedClassesProcessor> processors = myDerivedClassesRequests.get(sortedID);
        LOG.assertTrue(processors != null, uClass.getClass().getName());
        Query<PsiClass> search = ClassInheritorsSearch.search(uClass.getJavaPsi(), searchScope, false);
        if (Registry.is("batch.inspections.process.external.usages.in.parallel")) search = search.allowParallelProcessing();
        search.forEach(createMembersProcessor(processors, scope));
      }

      myDerivedClassesRequests = null;
    }

    if (myDerivedMethodsRequests != null) {
      List<SmartPsiElementPointer<?>> sortedIDs = getSortedIDs(myDerivedMethodsRequests);
      for (SmartPsiElementPointer<?> sortedID : sortedIDs) {
        UMethod uMethod = ReadAction.compute(() -> UastContextKt.toUElement(dereferenceInReadAction(sortedID), UMethod.class));
        if (uMethod == null) continue;
        RefMethod refMethod = ReadAction.compute(() -> (RefMethod)refManager.getReference(uMethod.getSourcePsi()));

        context.incrementJobDoneAmount(context.getStdJobDescriptors().FIND_EXTERNAL_USAGES, refManager.getQualifiedName(refMethod));

        List<DerivedMethodsProcessor> processors = myDerivedMethodsRequests.get(sortedID);
        LOG.assertTrue(processors != null, uMethod.getClass().getName());
        Query<PsiMethod> search = OverridingMethodsSearch.search(uMethod.getJavaPsi(), searchScope, true);
        if (Registry.is("batch.inspections.process.external.usages.in.parallel")) search = search.allowParallelProcessing();
        search.forEach(createMembersProcessor(processors, scope));
      }

      myDerivedMethodsRequests = null;
    }

    if (myFieldUsagesRequests != null) {
      List<SmartPsiElementPointer<?>> sortedIDs = getSortedIDs(myFieldUsagesRequests);
      for (SmartPsiElementPointer<?> sortedID : sortedIDs) {
        PsiElement field = dereferenceInReadAction(sortedID);
        if (field == null) continue;
        List<UsagesProcessor> processors = myFieldUsagesRequests.get(sortedID);

        LOG.assertTrue(processors != null, field.getClass().getName());
        context.incrementJobDoneAmount(context.getStdJobDescriptors().FIND_EXTERNAL_USAGES, refManager.getQualifiedName(refManager.getReference(field)));

        Query<PsiReference> search = ReferencesSearch.search(field, searchScope, false);
        if (Registry.is("batch.inspections.process.external.usages.in.parallel")) search = search.allowParallelProcessing();
        search.forEach(new PsiReferenceProcessorAdapter(createReferenceProcessor(processors, context)));
      }

      myFieldUsagesRequests = null;
    }

    if (myClassUsagesRequests != null) {
      List<SmartPsiElementPointer<?>> sortedIDs = getSortedIDs(myClassUsagesRequests);
      for (SmartPsiElementPointer<?> sortedID : sortedIDs) {
        PsiElement classDeclaration = dereferenceInReadAction(sortedID);
        if (classDeclaration == null) continue;
        List<UsagesProcessor> processors = myClassUsagesRequests.get(sortedID);

        LOG.assertTrue(processors != null, classDeclaration.getClass().getName());
        UClass uClass = ReadAction.compute(() -> UastContextKt.toUElement(classDeclaration, UClass.class));
        String name = getClassPresentableName(uClass);
        context.incrementJobDoneAmount(context.getStdJobDescriptors().FIND_EXTERNAL_USAGES, name);

        Query<PsiReference> search = ReferencesSearch.search(classDeclaration, searchScope, false);
        if (Registry.is("batch.inspections.process.external.usages.in.parallel")) search = search.allowParallelProcessing();
        search.forEach(new PsiReferenceProcessorAdapter(createReferenceProcessor(processors, context)));
      }

      myClassUsagesRequests = null;
    }

    if (myMethodUsagesRequests != null) {
      List<SmartPsiElementPointer<?>> sortedIDs = getSortedIDs(myMethodUsagesRequests);
      for (SmartPsiElementPointer<?> sortedID : sortedIDs) {
        UMethod uMethod = ReadAction.compute(() -> UastContextKt.toUElement(dereferenceInReadAction(sortedID), UMethod.class));
        if (uMethod == null) continue;
        List<UsagesProcessor> processors = myMethodUsagesRequests.get(sortedID);

        LOG.assertTrue(processors != null, uMethod.getClass().getName());
        context.incrementJobDoneAmount(context.getStdJobDescriptors().FIND_EXTERNAL_USAGES, ReadAction.compute(() -> uMethod.getName()));

        PsiMethod javaMethod = ReadAction.compute(() -> uMethod.getJavaPsi());
        Query<PsiReference> search = MethodReferencesSearch.search(javaMethod, searchScope, true);
        if (Registry.is("batch.inspections.process.external.usages.in.parallel")) search = search.allowParallelProcessing();
        search.forEach(new PsiReferenceProcessorAdapter(createReferenceProcessor(processors, context)));
      }

      myMethodUsagesRequests = null;
    }

    if (myQNameUsagesRequests != null) {
      PsiSearchHelper helper = PsiSearchHelper.getInstance(refManager.getProject());
      RefJavaManager javaManager = refManager.getExtension(RefJavaManager.MANAGER);
      List<SmartPsiElementPointer<?>> sortedIDs = getSortedIDs(myQNameUsagesRequests);
      for (SmartPsiElementPointer<?> id : sortedIDs) {
        UClass uClass = ReadAction.compute(() -> UastContextKt.toUElement(dereferenceInReadAction(id), UClass.class));
        String qualifiedName = uClass != null ? ReadAction.compute(() -> uClass.getQualifiedName()) : null;
        if (qualifiedName != null) {
          List<Runnable> callbacks = myQNameUsagesRequests.get(id);
          GlobalSearchScope projectScope = GlobalSearchScope.projectScope(context.getProject());
          PsiNonJavaFileReferenceProcessor processor = (file, startOffset, endOffset) -> {
            for (Runnable callback : callbacks) {
              callback.run();
            }
            return false;
          };

          DelegatingGlobalSearchScope globalSearchScope = new DelegatingGlobalSearchScope(projectScope) {
            final Set<FileType> fileTypes = javaManager.getLanguages().stream().map(l -> l.getAssociatedFileType()).collect(Collectors.toSet());

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

  private static @NotNull String getClassPresentableName(@NotNull UClass uClass) {
    return ReadAction.compute(() -> {
      String qualifiedName = uClass.getQualifiedName();
      if (qualifiedName != null) {
        return qualifiedName;
      }
      return Objects.requireNonNull(uClass.getName(), uClass.getClass().getName());
    });
  }

  private static PsiElement dereferenceInReadAction(@NotNull SmartPsiElementPointer<?> sortedID) {
    return ReadAction.compute(() -> sortedID.getElement());
  }

  private static @NotNull <Member extends PsiMember, P extends Processor<Member>> PsiElementProcessorAdapter<Member> createMembersProcessor(@NotNull List<P> processors,
                                                                                                                                            @NotNull AnalysisScope scope) {
    return new PsiElementProcessorAdapter<>(member -> {
      if (scope.contains(member)) return true;
      List<P> processorsArrayed = new ArrayList<>(processors);
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

  private static List<SmartPsiElementPointer<?>> getSortedIDs(Map<SmartPsiElementPointer<?>, ?> requests) {
    List<SmartPsiElementPointer<?>> result = new ArrayList<>();

    ApplicationManager.getApplication().runReadAction(() -> {
      for (SmartPsiElementPointer<?> id : requests.keySet()) {
        if (id != null && id.getContainingFile() != null) {
          result.add(id);
        }
      }
      result.sort((o1, o2) -> {
        PsiFile psiFile1 = o1.getContainingFile();
        LOG.assertTrue(psiFile1 != null);
        PsiFile psiFile2 = o2.getContainingFile();
        LOG.assertTrue(psiFile2 != null);
        return psiFile1.getName().compareTo(psiFile2.getName());
      });
    });

    return result;
  }

  private static PsiReferenceProcessor createReferenceProcessor(@NotNull List<UsagesProcessor> processors,
                                                                @NotNull GlobalInspectionContext context) {
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
  public void performPreRunActivities(@NotNull List<Tools> globalTools,
                                      @NotNull List<Tools> localTools,
                                      @NotNull GlobalInspectionContext context) {
    if (globalTools.stream().anyMatch(tools -> {
      InspectionProfileEntry tool = tools.getTool().getTool();
      return tool instanceof GlobalInspectionTool && ((GlobalInspectionTool)tool).isGraphNeeded();
    })) {
      getEntryPointsManager(context.getRefManager()).resolveEntryPoints(context.getRefManager());
    }
    // UnusedDeclarationInspection should run first
    for (int i = 0; i < globalTools.size(); i++) {
      InspectionToolWrapper<?,?> toolWrapper = globalTools.get(i).getTool();
      if (UnusedDeclarationInspectionBase.SHORT_NAME.equals(toolWrapper.getShortName())) {
        Collections.swap(globalTools, i, 0);
        break;
      }
    }
  }



  @Override
  public void performPostRunActivities(@NotNull List<InspectionToolWrapper<?, ?>> needRepeatSearchRequest, @NotNull GlobalInspectionContext context) {
    long timestamp = System.currentTimeMillis();
    JobDescriptor progress = context.getStdJobDescriptors().FIND_EXTERNAL_USAGES;
    progress.setTotalAmount(getRequestCount());

    do {
      processSearchRequests(context);
      InspectionToolWrapper<?,?>[] requestors = needRepeatSearchRequest.toArray(InspectionToolWrapper.EMPTY_ARRAY);
      InspectionManager inspectionManager = InspectionManager.getInstance(context.getProject());
      for (InspectionToolWrapper<?,?> toolWrapper : requestors) {
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
    LOG.info("Processing external usages finished in " + (System.currentTimeMillis() - timestamp) + " ms");
  }

}
