/*
 * User: anna
 * Date: 19-Dec-2007
 */
package com.intellij.codeInspection.ex;

import com.intellij.CommonBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.GlobalJavaInspectionContext;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.deadCode.DeadCodeInspection;
import com.intellij.codeInspection.reference.*;
import com.intellij.ide.util.projectWizard.JdkChooserPanel;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
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


  public void enqueueClassUsagesProcessor(RefClass refClass, UsagesProcessor p) {
    if (myClassUsagesRequests == null) myClassUsagesRequests = new THashMap<SmartPsiElementPointer, List<UsagesProcessor>>();
    enqueueRequestImpl(refClass, myClassUsagesRequests, p);
  }

  public void enqueueDerivedClassesProcessor(RefClass refClass, DerivedClassesProcessor p) {
    if (myDerivedClassesRequests == null) myDerivedClassesRequests = new THashMap<SmartPsiElementPointer, List<DerivedClassesProcessor>>();
    enqueueRequestImpl(refClass, myDerivedClassesRequests, p);
  }

  public void enqueueDerivedMethodsProcessor(RefMethod refMethod, DerivedMethodsProcessor p) {
    if (refMethod.isConstructor() || refMethod.isStatic()) return;
    if (myDerivedMethodsRequests == null) myDerivedMethodsRequests = new THashMap<SmartPsiElementPointer, List<DerivedMethodsProcessor>>();
    enqueueRequestImpl(refMethod, myDerivedMethodsRequests, p);
  }

  public void enqueueFieldUsagesProcessor(RefField refField, UsagesProcessor p) {
    if (myFieldUsagesRequests == null) myFieldUsagesRequests = new THashMap<SmartPsiElementPointer, List<UsagesProcessor>>();
    enqueueRequestImpl(refField, myFieldUsagesRequests, p);
  }

  public void enqueueMethodUsagesProcessor(RefMethod refMethod, UsagesProcessor p) {
    if (myMethodUsagesRequests == null) myMethodUsagesRequests = new THashMap<SmartPsiElementPointer, List<UsagesProcessor>>();
    enqueueRequestImpl(refMethod, myMethodUsagesRequests, p);
  }

  public EntryPointsManager getEntryPointsManager(final RefManager manager) {
    return manager.getExtension(RefJavaManager.MANAGER).getEntryPointsManager();
  }

  @SuppressWarnings({"UseOfSystemOutOrSystemErr"})
  public static boolean isInspectionsEnabled(final boolean online, Project project) {
    if (online) {
      while (JavaPsiFacade.getInstance(project).findClass("java.lang.Object", GlobalSearchScope.allScope(project)) == null) {
        if (ModuleManager.getInstance(project).getModules().length == 0) {
          Messages.showMessageDialog(project, InspectionsBundle.message("inspection.no.modules.error.message"),
                                     CommonBundle.message("title.error"), Messages.getErrorIcon());
          return false;
        }
        Messages.showMessageDialog(project, InspectionsBundle.message("inspection.no.jdk.error.message"),
                                   CommonBundle.message("title.error"), Messages.getErrorIcon());
        final Sdk projectJdk = JdkChooserPanel.chooseAndSetJDK(project);
        if (projectJdk == null) return false;
      }
    }
    else {
      PsiClass psiObjectClass = JavaPsiFacade.getInstance(project).findClass("java.lang.Object", GlobalSearchScope.allScope(project));
      if (psiObjectClass == null) {
        if (ModuleManager.getInstance(project).getModules().length == 0) {
          System.err.println(InspectionsBundle.message("inspection.no.modules.error.message"));
          return false;
        }
        System.err.println(InspectionsBundle.message("inspection.no.jdk.error.message"));
        System.err.println(
          InspectionsBundle.message("offline.inspections.jdk.not.found", ProjectRootManager.getInstance(project).getProjectJdkName()));
        return false;
      }
      final Module[] modules = ModuleManager.getInstance(project).getModules();
      for (Module module : modules) {
        final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
        final Sdk jdk = rootManager.getSdk();
        final OrderEntry[] entries = rootManager.getOrderEntries();
        for (OrderEntry entry : entries) {
          if (entry instanceof JdkOrderEntry) {
            if (jdk == null) {
              System.err.println(InspectionsBundle.message("offline.inspections.module.jdk.not.found", ((JdkOrderEntry)entry).getJdkName(),
                                                           module.getName()));
              return false;
            }
          }
          else if (entry instanceof LibraryOrderEntry) {
            final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)entry;
            final Library library = libraryOrderEntry.getLibrary();
            if (library == null || library.getFiles(OrderRootType.CLASSES).length != library.getUrls(OrderRootType.CLASSES).length) {
              System.err.println(InspectionsBundle.message("offline.inspections.library.was.not.resolved",
                                                           libraryOrderEntry.getPresentableName(), module.getName()));
            }
          }
        }
      }
    }
    return true;
  }

  private static <T extends Processor> void enqueueRequestImpl(RefElement refElement, Map<SmartPsiElementPointer, List<T>> requestMap, T processor) {
    List<T> requests = requestMap.get(refElement.getPointer());
    if (requests == null) {
      requests = new ArrayList<T>();
      requestMap.put(refElement.getPointer(), requests);
    }
    requests.add(processor);
  }

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

    final SearchScope searchScope = new GlobalSearchScope() {
      public boolean contains(VirtualFile file) {
        return !scope.contains(file) || file.getFileType() != StdFileTypes.JAVA;
      }

      public int compare(VirtualFile file1, VirtualFile file2) {
        return 0;
      }

      public boolean isSearchInModuleContent(@NotNull Module aModule) {
        return true;
      }

      public boolean isSearchInLibraries() {
        return false;
      }
    };

    if (myDerivedClassesRequests != null) {
      List<SmartPsiElementPointer> sortedIDs = getSortedIDs(myDerivedClassesRequests);
      for (SmartPsiElementPointer sortedID : sortedIDs) {
        final PsiClass psiClass = (PsiClass)sortedID.getElement();
        ((GlobalInspectionContextImpl)context).incrementJobDoneAmount(GlobalInspectionContextImpl.FIND_EXTERNAL_USAGES, ApplicationManager.getApplication().runReadAction(
            new Computable<String>() {
              public String compute() {
                return psiClass.getQualifiedName();
              }
            }
        ));

        final List<DerivedClassesProcessor> processors = myDerivedClassesRequests.get(sortedID);
        ClassInheritorsSearch.search(psiClass, searchScope, false)
          .forEach(new PsiElementProcessorAdapter<PsiClass>(new PsiElementProcessor<PsiClass>() {
            public boolean execute(PsiClass inheritor) {
              if (scope.contains(inheritor)) return true;
              DerivedClassesProcessor[] processorsArrayed = processors.toArray(new DerivedClassesProcessor[processors.size()]);
              for (DerivedClassesProcessor processor : processorsArrayed) {
                if (!processor.process(inheritor)) {
                  processors.remove(processor);
                }
              }
              return !processors.isEmpty();
            }
          }));
      }

      myDerivedClassesRequests = null;
    }

    if (myDerivedMethodsRequests != null) {
      List<SmartPsiElementPointer> sortedIDs = getSortedIDs(myDerivedMethodsRequests);
      for (SmartPsiElementPointer sortedID : sortedIDs) {
        final PsiMethod psiMethod = (PsiMethod)sortedID.getElement();
        final RefMethod refMethod = (RefMethod)refManager.getReference(psiMethod);

        ((GlobalInspectionContextImpl)context)
          .incrementJobDoneAmount(GlobalInspectionContextImpl.FIND_EXTERNAL_USAGES, refManager.getQualifiedName(refMethod));

        final List<DerivedMethodsProcessor> processors = myDerivedMethodsRequests.get(sortedID);
        OverridingMethodsSearch.search(psiMethod, searchScope, true)
          .forEach(new PsiElementProcessorAdapter<PsiMethod>(new PsiElementProcessor<PsiMethod>() {
            public boolean execute(PsiMethod derivedMethod) {
              if (scope.contains(derivedMethod)) return true;
              DerivedMethodsProcessor[] processorsArrayed = processors.toArray(new DerivedMethodsProcessor[processors.size()]);
              for (DerivedMethodsProcessor processor : processorsArrayed) {
                if (!processor.process(derivedMethod)) {
                  processors.remove(processor);
                }
              }

              return !processors.isEmpty();
            }
          }));
      }

      myDerivedMethodsRequests = null;
    }

    if (myFieldUsagesRequests != null) {
      List<SmartPsiElementPointer> sortedIDs = getSortedIDs(myFieldUsagesRequests);
      for (SmartPsiElementPointer sortedID : sortedIDs) {
        final PsiField psiField = (PsiField)sortedID.getElement();
        final List<UsagesProcessor> processors = myFieldUsagesRequests.get(sortedID);

        ((GlobalInspectionContextImpl)context)
          .incrementJobDoneAmount(GlobalInspectionContextImpl.FIND_EXTERNAL_USAGES, refManager.getQualifiedName(refManager.getReference(psiField)));

        ReferencesSearch.search(psiField, searchScope, false)
          .forEach(new PsiReferenceProcessorAdapter(createReferenceProcessor(processors, context)));
      }

      myFieldUsagesRequests = null;
    }

    if (myClassUsagesRequests != null) {
      List<SmartPsiElementPointer> sortedIDs = getSortedIDs(myClassUsagesRequests);
      for (SmartPsiElementPointer sortedID : sortedIDs) {
        final PsiClass psiClass = (PsiClass)sortedID.getElement();
        final List<UsagesProcessor> processors = myClassUsagesRequests.get(sortedID);

        ((GlobalInspectionContextImpl)context).incrementJobDoneAmount(GlobalInspectionContextImpl.FIND_EXTERNAL_USAGES, ApplicationManager.getApplication().runReadAction(
            new Computable<String>() {
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
        final PsiMethod psiMethod = (PsiMethod)sortedID.getElement();
        final List<UsagesProcessor> processors = myMethodUsagesRequests.get(sortedID);

        ((GlobalInspectionContextImpl)context)
          .incrementJobDoneAmount(GlobalInspectionContextImpl.FIND_EXTERNAL_USAGES, refManager.getQualifiedName(refManager.getReference(psiMethod)));

        MethodReferencesSearch.search(psiMethod, searchScope, true)
          .forEach(new PsiReferenceProcessorAdapter(createReferenceProcessor(processors, context)));
      }

      myMethodUsagesRequests = null;
    }
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

  private static PsiReferenceProcessor createReferenceProcessor(final List<UsagesProcessor> processors,
                                                                final GlobalInspectionContext context) {
    return new PsiReferenceProcessor() {
      public boolean execute(PsiReference reference) {
        AnalysisScope scope = context.getRefManager().getScope();
        if ((scope.contains(reference.getElement()) && reference.getElement().getLanguage() == StdLanguages.JAVA) ||
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

  public void performPreRunActivities(final List<InspectionProfileEntry> globalTools, final List<InspectionProfileEntry> localTools,
                                      final GlobalInspectionContext context) {
    getEntryPointsManager(context.getRefManager()).resolveEntryPoints(context.getRefManager());
    Collections.sort(globalTools, new Comparator<InspectionProfileEntry>() {
      public int compare(final InspectionProfileEntry o1, final InspectionProfileEntry o2) {
        if (o1 instanceof DeadCodeInspection) return -1;
        if (o2 instanceof DeadCodeInspection) return 1;
        return 0;
      }
    });
  }



  public void performPostRunActivities(List<InspectionProfileEntry> needRepeatSearchRequest, final GlobalInspectionContext context) {
    GlobalInspectionContextImpl.FIND_EXTERNAL_USAGES.setTotalAmount(getRequestCount() * 2);

    do {
      processSearchRequests(context);
      InspectionProfileEntry[] requestors = needRepeatSearchRequest.toArray(new InspectionProfileEntry[needRepeatSearchRequest.size()]);
      for (InspectionProfileEntry requestor : requestors) {
        if (requestor instanceof InspectionTool &&
            !((InspectionTool)requestor).queryExternalUsagesRequests(InspectionManagerEx.getInstance(context.getProject()))) {
          needRepeatSearchRequest.remove(requestor);
        }
      }
      int oldSearchRequestCount = GlobalInspectionContextImpl.FIND_EXTERNAL_USAGES.getTotalAmount();
      float proportion = GlobalInspectionContextImpl.FIND_EXTERNAL_USAGES.getProgress();
      int totalAmount = oldSearchRequestCount + getRequestCount() * 2;
      GlobalInspectionContextImpl.FIND_EXTERNAL_USAGES.setTotalAmount(totalAmount);
      GlobalInspectionContextImpl.FIND_EXTERNAL_USAGES.setDoneAmount((int)(totalAmount * proportion));
    }
    while (!needRepeatSearchRequest.isEmpty());
  }

}