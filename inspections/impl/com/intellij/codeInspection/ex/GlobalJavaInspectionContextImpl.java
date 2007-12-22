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
import com.intellij.codeInspection.reference.*;
import com.intellij.ide.util.projectWizard.JdkChooserPanel;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiVariableEx;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GlobalJavaInspectionContextImpl extends GlobalJavaInspectionContext {
  private static final Logger LOG = Logger.getInstance("#" + GlobalJavaInspectionContextImpl.class.getName());

  private THashMap<PsiElement, List<DerivedMethodsProcessor>> myDerivedMethodsRequests;
  private THashMap<PsiElement, List<DerivedClassesProcessor>> myDerivedClassesRequests;
  private THashMap<PsiElement, List<UsagesProcessor>> myMethodUsagesRequests;
  private THashMap<PsiElement, List<UsagesProcessor>> myFieldUsagesRequests;
  private THashMap<PsiElement, List<UsagesProcessor>> myClassUsagesRequests;
  @NonNls public static final String SUPPRESS_INSPECTIONS_TAG_NAME = "noinspection";
  public static final String SUPPRESS_INSPECTIONS_ANNOTATION_NAME = "java.lang.SuppressWarnings";
  @NonNls public static final Pattern SUPPRESS_IN_LINE_COMMENT_PATTERN =
    Pattern.compile("//\\s*" + SUPPRESS_INSPECTIONS_TAG_NAME + "\\s+(\\w+(s*,\\w+)*)");


  public void enqueueClassUsagesProcessor(RefClass refClass, UsagesProcessor p) {
    if (myClassUsagesRequests == null) myClassUsagesRequests = new THashMap<PsiElement, List<UsagesProcessor>>();
    enqueueRequestImpl(refClass, myClassUsagesRequests, p);
  }

  public void enqueueDerivedClassesProcessor(RefClass refClass, DerivedClassesProcessor p) {
    if (myDerivedClassesRequests == null) myDerivedClassesRequests = new THashMap<PsiElement, List<DerivedClassesProcessor>>();
    enqueueRequestImpl(refClass, myDerivedClassesRequests, p);
  }

  public void enqueueDerivedMethodsProcessor(RefMethod refMethod, DerivedMethodsProcessor p) {
    if (refMethod.isConstructor() || refMethod.isStatic()) return;
    if (myDerivedMethodsRequests == null) myDerivedMethodsRequests = new THashMap<PsiElement, List<DerivedMethodsProcessor>>();
    enqueueRequestImpl(refMethod, myDerivedMethodsRequests, p);
  }

  public void enqueueFieldUsagesProcessor(RefField refField, UsagesProcessor p) {
    if (myFieldUsagesRequests == null) myFieldUsagesRequests = new THashMap<PsiElement, List<UsagesProcessor>>();
    enqueueRequestImpl(refField, myFieldUsagesRequests, p);
  }

  public void enqueueMethodUsagesProcessor(RefMethod refMethod, UsagesProcessor p) {
    if (myMethodUsagesRequests == null) myMethodUsagesRequests = new THashMap<PsiElement, List<UsagesProcessor>>();
    enqueueRequestImpl(refMethod, myMethodUsagesRequests, p);
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
        final ProjectJdk projectJdk = JdkChooserPanel.chooseAndSetJDK(project);
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
        final ProjectJdk jdk = rootManager.getJdk();
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

  private static <T extends Processor> void enqueueRequestImpl(RefElement refElement, Map<PsiElement, List<T>> requestMap, T processor) {
    List<T> requests = requestMap.get(refElement.getElement());
    if (requests == null) {
      requests = new ArrayList<T>();
      requestMap.put(refElement.getElement(), requests);
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
      List<PsiElement> sortedIDs = getSortedIDs(myDerivedClassesRequests);
      for (PsiElement sortedID : sortedIDs) {
        final PsiClass psiClass = (PsiClass)sortedID;
        ((GlobalInspectionContextImpl)context).incrementJobDoneAmount(GlobalInspectionContextImpl.FIND_EXTERNAL_USAGES, psiClass.getQualifiedName());

        final List<DerivedClassesProcessor> processors = myDerivedClassesRequests.get(psiClass);
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
      List<PsiElement> sortedIDs = getSortedIDs(myDerivedMethodsRequests);
      for (PsiElement sortedID : sortedIDs) {
        final PsiMethod psiMethod = (PsiMethod)sortedID;
        final RefMethod refMethod = (RefMethod)refManager.getReference(psiMethod);

        ((GlobalInspectionContextImpl)context)
          .incrementJobDoneAmount(GlobalInspectionContextImpl.FIND_EXTERNAL_USAGES, RefUtil.getInstance().getQualifiedName(refMethod));

        final List<DerivedMethodsProcessor> processors = myDerivedMethodsRequests.get(psiMethod);
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
      List<PsiElement> sortedIDs = getSortedIDs(myFieldUsagesRequests);
      for (PsiElement sortedID : sortedIDs) {
        final PsiField psiField = (PsiField)sortedID;
        final List<UsagesProcessor> processors = myFieldUsagesRequests.get(psiField);

        ((GlobalInspectionContextImpl)context)
          .incrementJobDoneAmount(GlobalInspectionContextImpl.FIND_EXTERNAL_USAGES, RefUtil.getInstance().getQualifiedName(refManager.getReference(psiField)));

        ReferencesSearch.search(psiField, searchScope, false)
          .forEach(new PsiReferenceProcessorAdapter(createReferenceProcessor(processors, context)));
      }

      myFieldUsagesRequests = null;
    }

    if (myClassUsagesRequests != null) {
      List<PsiElement> sortedIDs = getSortedIDs(myClassUsagesRequests);
      for (PsiElement sortedID : sortedIDs) {
        final PsiClass psiClass = (PsiClass)sortedID;
        final List<UsagesProcessor> processors = myClassUsagesRequests.get(psiClass);

        ((GlobalInspectionContextImpl)context).incrementJobDoneAmount(GlobalInspectionContextImpl.FIND_EXTERNAL_USAGES, psiClass.getQualifiedName());

        ReferencesSearch.search(psiClass, searchScope, false)
          .forEach(new PsiReferenceProcessorAdapter(createReferenceProcessor(processors, context)));
      }

      myClassUsagesRequests = null;
    }

    if (myMethodUsagesRequests != null) {
      List<PsiElement> sortedIDs = getSortedIDs(myMethodUsagesRequests);
      for (PsiElement sortedID : sortedIDs) {
        final PsiMethod psiMethod = (PsiMethod)sortedID;
        final List<UsagesProcessor> processors = myMethodUsagesRequests.get(psiMethod);

        ((GlobalInspectionContextImpl)context)
          .incrementJobDoneAmount(GlobalInspectionContextImpl.FIND_EXTERNAL_USAGES, RefUtil.getInstance().getQualifiedName(refManager.getReference(psiMethod)));

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

  private static List<PsiElement> getSortedIDs(final Map<PsiElement, ?> requests) {
    final List<PsiElement> result = new ArrayList<PsiElement>();

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        for (PsiElement id : requests.keySet()) {
          if (id != null && id.isValid()) {
            result.add(id);
          }
        }
        Collections.sort(result, new Comparator<PsiElement>() {
          public int compare(PsiElement o1, PsiElement o2) {
            final PsiFile psiFile1 = o1.getContainingFile();
            LOG.assertTrue(psiFile1 != null);
            final PsiFile psiFile2 = o2.getContainingFile();
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

  public static boolean isToCheckMember(PsiDocCommentOwner owner, @NonNls String inspectionToolID) {
    return getElementMemberSuppressedIn(owner, inspectionToolID) == null;
  }

  @Nullable
  public static PsiElement getElementMemberSuppressedIn(final PsiDocCommentOwner owner, final String inspectionToolID) {
    PsiElement element = getDocCommentToolSuppressedIn(owner, inspectionToolID);
    if (element != null) return element;
    element = getAnnotationMemberSuppressedIn(owner, inspectionToolID);
    if (element != null) return element;
    PsiDocCommentOwner classContainer = PsiTreeUtil.getParentOfType(owner, PsiClass.class);
    while (classContainer != null) {
      element = getDocCommentToolSuppressedIn(classContainer, inspectionToolID);
      if (element != null) return element;

      element = getAnnotationMemberSuppressedIn(classContainer, inspectionToolID);
      if (element != null) return element;

      classContainer = PsiTreeUtil.getParentOfType(classContainer, PsiClass.class);
    }
    return null;
  }

  @Nullable
  public static PsiElement getAnnotationMemberSuppressedIn(final PsiModifierListOwner owner, final String inspectionToolID) {
    PsiModifierList modifierList = owner.getModifierList();
    Collection<String> suppressedIds = getInspectionIdsSuppressedInAnnotation(modifierList);
    for (String ids : suppressedIds) {
      if (isInspectionToolIdMentioned(ids, inspectionToolID)) {
        return modifierList != null ? modifierList.findAnnotation(SUPPRESS_INSPECTIONS_ANNOTATION_NAME) : null;
      }
    }
    return null;
  }

  @Nullable
  public static PsiElement getDocCommentToolSuppressedIn(final PsiDocCommentOwner owner, final String inspectionToolID) {
    PsiDocComment docComment = owner.getDocComment();
    if (docComment != null) {
      PsiDocTag inspectionTag = docComment.findTagByName(SUPPRESS_INSPECTIONS_TAG_NAME);
      if (inspectionTag != null) {
        final PsiElement[] dataElements = inspectionTag.getDataElements();
        for (PsiElement dataElement : dataElements) {
          String valueText = dataElement.getText();
          if (isInspectionToolIdMentioned(valueText, inspectionToolID)) {
            return docComment;
          }
        }
      }
    }
    return null;
  }

  public static boolean isInspectionToolIdMentioned(String inspectionsList, String inspectionToolID) {
    Iterable<String> ids = StringUtil.tokenize(inspectionsList, "[,]");

    for (@NonNls String id : ids) {
      if (id.equals(inspectionToolID) || id.equals("ALL")) return true;
    }
    return false;
  }

  @NotNull
  public static Collection<String> getInspectionIdsSuppressedInAnnotation(final PsiModifierListOwner owner) {
    if (LanguageLevel.JDK_1_5.compareTo(PsiUtil.getLanguageLevel(owner)) > 0) return Collections.emptyList();
    PsiModifierList modifierList = owner.getModifierList();
    return getInspectionIdsSuppressedInAnnotation(modifierList);
  }

  @NotNull
  public static Collection<String> getInspectionIdsSuppressedInAnnotation(final PsiModifierList modifierList) {
    if (modifierList == null) {
      return Collections.emptyList();
    }
    PsiAnnotation annotation = modifierList.findAnnotation(SUPPRESS_INSPECTIONS_ANNOTATION_NAME);
    if (annotation == null) {
      return Collections.emptyList();
    }
    final PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
    if (attributes.length == 0) {
      return Collections.emptyList();
    }
    final PsiAnnotationMemberValue attributeValue = attributes[0].getValue();
    Collection<String> result = new ArrayList<String>();
    if (attributeValue instanceof PsiArrayInitializerMemberValue) {
      final PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue)attributeValue).getInitializers();
      for (PsiAnnotationMemberValue annotationMemberValue : initializers) {
        final String id = getInspectionIdSuppressedInAnnotationAttribute(annotationMemberValue);
        if (id != null) {
          result.add(id);
        }
      }
    }
    else {
      final String id = getInspectionIdSuppressedInAnnotationAttribute(attributeValue);
      if (id != null) {
        result.add(id);
      }
    }
    return result;
  }

  @Nullable
  public static String getInspectionIdSuppressedInAnnotationAttribute(PsiElement element) {
    if (element instanceof PsiLiteralExpression) {
      final Object value = ((PsiLiteralExpression)element).getValue();
      if (value instanceof String) {
        return (String)value;
      }
    }
    else if (element instanceof PsiReferenceExpression) {
      final PsiElement psiElement = ((PsiReferenceExpression)element).resolve();
      if (psiElement instanceof PsiVariableEx) {
        final Object val = ((PsiVariableEx)psiElement).computeConstantValue(new HashSet<PsiVariable>());
        if (val instanceof String) {
          return (String)val;
        }
      }
    }
    return null;
  }

  @Nullable
  public static String getSuppressedInspectionIdsIn(PsiElement element) {
    if (element instanceof PsiComment) {
      String text = element.getText();
      Matcher matcher = SUPPRESS_IN_LINE_COMMENT_PATTERN.matcher(text);
      if (matcher.matches()) {
        return matcher.group(1);
      }
    }
    if (element instanceof PsiDocCommentOwner) {
      PsiDocComment docComment = ((PsiDocCommentOwner)element).getDocComment();
      if (docComment != null) {
        PsiDocTag inspectionTag = docComment.findTagByName(SUPPRESS_INSPECTIONS_TAG_NAME);
        if (inspectionTag != null) {
          String valueText = "";
          for (PsiElement dataElement : inspectionTag.getDataElements()) {
            valueText += dataElement.getText();
          }
          return valueText;
        }
      }
    }
    if (element instanceof PsiModifierListOwner) {
      Collection<String> suppressedIds = getInspectionIdsSuppressedInAnnotation((PsiModifierListOwner)element);
      return suppressedIds.isEmpty() ? null : StringUtil.join(suppressedIds, ",");
    }
    return null;
  }

  @Nullable
  public static PsiElement getElementToolSuppressedIn(final PsiElement place, final String toolId) {
    if (place == null) return null;
    return ApplicationManager.getApplication().runReadAction(new Computable<PsiElement>() {
      @Nullable
      public PsiElement compute() {
        PsiStatement statement = PsiTreeUtil.getNonStrictParentOfType(place, PsiStatement.class);
        if (statement != null) {
          PsiElement prev = PsiTreeUtil.skipSiblingsBackward(statement, PsiWhiteSpace.class);
          if (prev instanceof PsiComment) {
            String text = prev.getText();
            Matcher matcher = SUPPRESS_IN_LINE_COMMENT_PATTERN.matcher(text);
            if (matcher.matches() && isInspectionToolIdMentioned(matcher.group(1), toolId)) {
              return prev;
            }
          }
        }

        PsiLocalVariable local = PsiTreeUtil.getParentOfType(place, PsiLocalVariable.class);
        if (local != null && getAnnotationMemberSuppressedIn(local, toolId) != null) {
          PsiModifierList modifierList = local.getModifierList();
          return modifierList != null ? modifierList.findAnnotation(SUPPRESS_INSPECTIONS_ANNOTATION_NAME) : null;
        }

        PsiElement container = PsiTreeUtil.getNonStrictParentOfType(place, PsiDocCommentOwner.class);
        while (true) {
          if (!(container instanceof PsiTypeParameter)) break;
          container = PsiTreeUtil.getParentOfType(container, PsiDocCommentOwner.class);
        }

        if (container != null) {
          PsiElement element = getElementMemberSuppressedIn((PsiDocCommentOwner)container, toolId);
          if (element != null) return element;
        }
        PsiDocCommentOwner classContainer = PsiTreeUtil.getParentOfType(container, PsiDocCommentOwner.class, true);
        if (classContainer != null) {
          PsiElement element = getElementMemberSuppressedIn(classContainer, toolId);
          if (element != null) return element;
        }

        return null;
      }
    });
  }
}