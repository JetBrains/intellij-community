/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.ex;

import com.intellij.CommonBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.ide.util.projectWizard.JdkChooserPanel;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.profile.Profile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashMap;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.regex.Pattern;

public class GlobalInspectionContextImpl implements GlobalInspectionContext {
  private RefManager myRefManager;
  private ContentManager myContentManager;
  private AnalysisScope myCurrentScope;

  private final Project myProject;
  private List<JobDescriptor> myJobDescriptors;
  private InspectionResultsView myView = null;
  private Content myContent = null;

  private HashMap<PsiElement, List<DerivedMethodsProcessor>> myDerivedMethodsRequests;
  private HashMap<PsiElement, List<DerivedClassesProcessor>> myDerivedClassesRequests;
  private HashMap<PsiElement, List<UsagesProcessor>> myMethodUsagesRequests;
  private HashMap<PsiElement, List<UsagesProcessor>> myFieldUsagesRequests;
  private HashMap<PsiElement, List<UsagesProcessor>> myClassUsagesRequests;
  private ProgressIndicator myProgressIndicator;


  public static final JobDescriptor BUILD_GRAPH = new JobDescriptor(InspectionsBundle.message("inspection.processing.job.descriptor"));
  public static final JobDescriptor FIND_EXTERNAL_USAGES =
    new JobDescriptor(InspectionsBundle.message("inspection.processing.job.descriptor1"));
  private static final JobDescriptor LOCAL_ANALYSIS = new JobDescriptor(InspectionsBundle.message("inspection.processing.job.descriptor2"));


  private InspectionProfile myExternalProfile = null;

  public boolean RUN_WITH_EDITOR_PROFILE = false;
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.GlobalInspectionContextImpl");
  @NonNls public static final String SUPPRESS_INSPECTIONS_TAG_NAME = "noinspection";
  public static final String SUPPRESS_INSPECTIONS_ANNOTATION_NAME = "java.lang.SuppressWarnings";
  @NonNls public static final Pattern SUPPRESS_IN_LINE_COMMENT_PATTERN =
    Pattern.compile("//\\s*" + SUPPRESS_INSPECTIONS_TAG_NAME + "\\s+(\\w+(,\\w+)*)");

  private Map<String, Set<InspectionTool>> myTools = new java.util.HashMap<String, Set<InspectionTool>>();

  private UIOptions myUIOptions;

  public GlobalInspectionContextImpl(Project project, ContentManager contentManager) {
    myProject = project;

    myUIOptions = ((InspectionManagerEx)InspectionManagerEx.getInstance(project)).getUIOptions().copy();
    myRefManager = null;
    myCurrentScope = null;
    myContentManager = contentManager;
    if (myContentManager != null) { //test || offline
      myContentManager.addContentManagerListener(new ContentManagerAdapter() {
        public void contentRemoved(ContentManagerEvent event) {
          if (event.getContent() == myContent){
            if (myView != null) {
              GlobalInspectionContextImpl.this.close(false);
            }
            myContent = null;
          }
        }
      });
    }
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public ContentManager getContentManager() {
    return myContentManager;
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    cleanup();
  }


  public InspectionProfile getCurrentProfile() {
    if (myExternalProfile != null) return myExternalProfile;
    InspectionManagerEx managerEx = (InspectionManagerEx)InspectionManagerEx.getInstance(myProject);
    final InspectionProjectProfileManager inspectionProfileManager = InspectionProjectProfileManager.getInstance(myProject);
    Profile profile = inspectionProfileManager.getProfile(managerEx.getCurrentProfile());
    if (profile == null) {
      if (inspectionProfileManager.useProjectLevelProfileSettings()) {
        profile = InspectionProfileManager.getInstance().getProfile(managerEx.getCurrentProfile());
        if (profile != null) return (InspectionProfile)profile;
      }
      final String[] avaliableProfileNames = inspectionProfileManager.getAvailableProfileNames();
      if (avaliableProfileNames == null || avaliableProfileNames.length == 0) {
        //can't be
        return null;
      }
      profile = inspectionProfileManager.getProfile(avaliableProfileNames[0]);
    }
    return (InspectionProfile)profile;
  }

  public boolean isSuppressed(RefEntity entity, String id) {
    if (entity instanceof RefElement) {
      final RefElement refElement = (RefElement)entity;
      return isSuppressed(refElement.getElement(), id);

    }
    return false;
  }

  public boolean isSuppressed(PsiElement element, String id) {
    if (element instanceof PsiDocCommentOwner) {
      return !isToCheckMember((PsiDocCommentOwner)element, id);
    }
    return false;
  }


  private void addView(InspectionResultsView view) {
    myView = view;
    ContentManager contentManager = getContentManager();
    myContent = PeerFactory.getInstance().getContentFactory().createContent(view,
                                                                            view.getCurrentProfileName() == null
                                                                            ? InspectionsBundle.message("inspection.results.title")
                                                                            : InspectionsBundle.message(
                                                                              "inspection.results.for.profile.toolwindow.title",
                                                                              view.getCurrentProfileName()),
                                                                            false);

    contentManager.addContent(myContent);
    contentManager.setSelectedContent(myContent);

    ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.INSPECTION).activate(null);
  }


  public void enqueueClassUsagesProcessor(RefClass refClass, UsagesProcessor p) {
    if (myClassUsagesRequests == null) myClassUsagesRequests = new HashMap<PsiElement, List<UsagesProcessor>>();
    enqueueRequestImpl(refClass, myClassUsagesRequests, p);
  }

  public void enqueueDerivedClassesProcessor(RefClass refClass, DerivedClassesProcessor p) {
    if (myDerivedClassesRequests == null) myDerivedClassesRequests = new HashMap<PsiElement, List<DerivedClassesProcessor>>();
    enqueueRequestImpl(refClass, myDerivedClassesRequests, p);
  }

  public void enqueueDerivedMethodsProcessor(RefMethod refMethod, DerivedMethodsProcessor p) {
    if (refMethod.isConstructor() || refMethod.isStatic()) return;
    if (myDerivedMethodsRequests == null) myDerivedMethodsRequests = new HashMap<PsiElement, List<DerivedMethodsProcessor>>();
    enqueueRequestImpl(refMethod, myDerivedMethodsRequests, p);
  }

  public void enqueueFieldUsagesProcessor(RefField refField, UsagesProcessor p) {
    if (myFieldUsagesRequests == null) myFieldUsagesRequests = new HashMap<PsiElement, List<UsagesProcessor>>();
    enqueueRequestImpl(refField, myFieldUsagesRequests, p);
  }

  public void enqueueMethodUsagesProcessor(RefMethod refMethod, UsagesProcessor p) {
    if (myMethodUsagesRequests == null) myMethodUsagesRequests = new HashMap<PsiElement, List<UsagesProcessor>>();
    enqueueRequestImpl(refMethod, myMethodUsagesRequests, p);
  }

  private static <T extends Processor> void enqueueRequestImpl(RefElement refElement, Map<PsiElement, List<T>> requestMap, T processor) {
    List<T> requests = requestMap.get(refElement.getElement());
    if (requests == null) {
      requests = new ArrayList<T>();
      requestMap.put(refElement.getElement(), requests);
    }
    requests.add(processor);
  }

  private void cleanup() {
    myProgressIndicator = null;

    myDerivedMethodsRequests = null;
    myDerivedClassesRequests = null;
    myMethodUsagesRequests = null;
    myFieldUsagesRequests = null;
    myClassUsagesRequests = null;

    myTools.clear();
    final InspectionProfile profile = myExternalProfile;
    if (profile == null) {
      if (myCurrentScope != null) {
        final Set<String> profiles = myCurrentScope.getActiveInspectionProfiles();
        InspectionProjectProfileManager inspectionProfileManager = InspectionProjectProfileManager.getInstance(myProject);
        for (String profileName : profiles) {
          ((InspectionProfile)inspectionProfileManager.getProfile(profileName)).cleanup();
        }
      }
    } else {
      InspectionProjectProfileManager.getInstance(myProject).getProfileWrapper(profile.getName()).getInspectionProfile().cleanup();
    }

    EntryPointsManager.getInstance(getProject()).cleanup();

    if (myRefManager != null) {
      ((RefManagerImpl)myRefManager).cleanup();
      myRefManager = null;
      if (myCurrentScope != null){
        myCurrentScope.invalidate();
        myCurrentScope = null;
      }
    }
  }

  public void setCurrentScope(AnalysisScope currentScope) {
    myCurrentScope = currentScope;
  }

  public void doInspections(final AnalysisScope scope, final InspectionManager manager) {
    while (PsiManager.getInstance(getProject()).findClass("java.lang.Object") == null) {
      if (ModuleManager.getInstance(getProject()).getModules().length == 0) {
        Messages.showMessageDialog(getProject(), InspectionsBundle.message("inspection.no.modules.error.message"),
                                   CommonBundle.message("title.error"), Messages.getErrorIcon());
        return;
      }
      Messages.showMessageDialog(getProject(), InspectionsBundle.message("inspection.no.jdk.error.message"),
                                 CommonBundle.message("title.error"), Messages.getErrorIcon());
      final ProjectJdk projectJdk = JdkChooserPanel.chooseAndSetJDK(myProject);
      if (projectJdk == null) return;
    }

    cleanup();
    getContentManager().removeContent(myContent);

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        myCurrentScope = scope;
        launchInspections(scope, manager);
      }
    });
  }


  @NotNull
  public RefManager getRefManager() {
    if (myRefManager == null) {
      myRefManager = new RefManagerImpl(myProject, myCurrentScope, this);
    }

    return myRefManager;
  }

  public void launchInspectionsOffline(final AnalysisScope scope,
                                       OutputStream outStream,
                                       final boolean runWithEditorSettings,
                                       final InspectionManager manager) {
    cleanup();

    myCurrentScope = scope;
    final Element root = new Element(InspectionsBundle.message("inspection.problems"));
    final Document doc = new Document(root);

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        performInspectionsWithProgress(scope, runWithEditorSettings, manager);
        InspectionProfileEntry[] tools = getCurrentProfile().getInspectionTools();
        for (InspectionProfileEntry tool : tools) {
          if (getCurrentProfile().isToolEnabled(HighlightDisplayKey.find(tool.getShortName()))) {
            ((InspectionTool)tool).exportResults(root);
          }
        }
      }
    });

    try {
      ((ProjectEx)getProject()).getMacroReplacements().substitute(doc.getRootElement(), SystemInfo.isFileSystemCaseSensitive);
      JDOMUtil.writeDocument(doc, outStream, "\n");
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public void processSearchRequests() {
    final PsiManager psiManager = PsiManager.getInstance(getProject());
    final PsiSearchHelper helper = psiManager.getSearchHelper();
    final RefManager refManager = getRefManager();
    final AnalysisScope scope = refManager.getScope();

    final ProgressIndicator progress =
      myProgressIndicator == null ? null : new ProgressWrapper(myProgressIndicator);
    ProgressManager.getInstance().runProcess(new Runnable() {
      public void run() {
        final SearchScope searchScope = new GlobalSearchScope() {
          public boolean contains(VirtualFile file) {
            return !scope.contains(file) || file.getFileType() != StdFileTypes.JAVA;
          }

          public int compare(VirtualFile file1, VirtualFile file2) {
            return 0;
          }

          public boolean isSearchInModuleContent(Module aModule) {
            return true;
          }

          public boolean isSearchInLibraries() {
            return false;
          }
        };
        if (myDerivedClassesRequests != null) {
          List<PsiElement> sortedIDs = getSortedIDs(myDerivedClassesRequests);
          for (PsiElement sortedID : sortedIDs) {
            PsiClass psiClass = (PsiClass)sortedID;
            incrementJobDoneAmount(FIND_EXTERNAL_USAGES, psiClass.getQualifiedName());

            final List<DerivedClassesProcessor> processors = myDerivedClassesRequests.get(psiClass);
            helper.processInheritors(new PsiElementProcessor<PsiClass>() {
              public boolean execute(PsiClass inheritor) {
                if (scope.contains(inheritor)) return true;
                DerivedClassesProcessor[] processorsArrayed = processors.toArray(new DerivedClassesProcessor[processors.size()]);
                for (DerivedClassesProcessor processor : processorsArrayed) {
                  if (!processor.process(inheritor)) {
                    processors.remove(processor);
                  }
                }
                return processors.size() > 0;
              }
            }, psiClass, searchScope, false);
          }

          myDerivedClassesRequests = null;
        }

        if (myDerivedMethodsRequests != null) {
          List<PsiElement> sortedIDs = getSortedIDs(myDerivedMethodsRequests);
          for (PsiElement sortedID : sortedIDs) {
            PsiMethod psiMethod = (PsiMethod)sortedID;
            final RefMethod refMethod = (RefMethod)refManager.getReference(psiMethod);

            incrementJobDoneAmount(FIND_EXTERNAL_USAGES, RefUtil.getInstance().getQualifiedName(refMethod));

            final List<DerivedMethodsProcessor> processors = myDerivedMethodsRequests.get(psiMethod);
            helper.processOverridingMethods(new PsiElementProcessor<PsiMethod>() {
              public boolean execute(PsiMethod derivedMethod) {
                if (scope.contains(derivedMethod)) return true;
                DerivedMethodsProcessor[] processorsArrayed = processors.toArray(new DerivedMethodsProcessor[processors.size()]);
                for (DerivedMethodsProcessor processor : processorsArrayed) {
                  if (!processor.process(derivedMethod)) {
                    processors.remove(processor);
                  }
                }

                return processors.size() > 0;
              }
            }, psiMethod, searchScope, true);
          }

          myDerivedMethodsRequests = null;
        }

        if (myFieldUsagesRequests != null) {
          List<PsiElement> sortedIDs = getSortedIDs(myFieldUsagesRequests);
          for (PsiElement sortedID : sortedIDs) {
            PsiField psiField = (PsiField)sortedID;
            final List<UsagesProcessor> processors = myFieldUsagesRequests.get(psiField);

            incrementJobDoneAmount(FIND_EXTERNAL_USAGES,
                                   RefUtil.getInstance().getQualifiedName(refManager.getReference(psiField)));

            helper.processReferences(createReferenceProcessor(processors), psiField, searchScope, false);
          }

          myFieldUsagesRequests = null;
        }

        if (myClassUsagesRequests != null) {
          List<PsiElement> sortedIDs = getSortedIDs(myClassUsagesRequests);
          for (PsiElement sortedID : sortedIDs) {
            PsiClass psiClass = (PsiClass)sortedID;
            final List<UsagesProcessor> processors = myClassUsagesRequests.get(psiClass);

            incrementJobDoneAmount(FIND_EXTERNAL_USAGES, psiClass.getQualifiedName());

            helper.processReferences(createReferenceProcessor(processors), psiClass, searchScope, false);
          }

          myClassUsagesRequests = null;
        }

        if (myMethodUsagesRequests != null) {
          List<PsiElement> sortedIDs = getSortedIDs(myMethodUsagesRequests);
          for (PsiElement sortedID : sortedIDs) {
            PsiMethod psiMethod = (PsiMethod)sortedID;
            final List<UsagesProcessor> processors = myMethodUsagesRequests.get(psiMethod);

            incrementJobDoneAmount(FIND_EXTERNAL_USAGES,
                                   RefUtil.getInstance().getQualifiedName(refManager.getReference(psiMethod)));

            helper.processReferencesIncludingOverriding(createReferenceProcessor(processors), psiMethod, searchScope);
          }

          myMethodUsagesRequests = null;
        }
      }
    }, progress);
  }

  public static boolean isToCheckMember(PsiDocCommentOwner owner, @NonNls String inspectionToolID) {
    return getElementMemberSuppressedIn(owner, inspectionToolID) == null;
  }

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

      classContainer = classContainer.getContainingClass();
    }
    return null;
  }

  public boolean isToCheckMember(PsiDocCommentOwner owner, InspectionTool tool) {
    if (RUN_WITH_EDITOR_PROFILE && InspectionProjectProfileManager.getInstance(owner.getProject()).getInspectionProfile(owner)
      .getInspectionTool(tool.getShortName()) != tool) {
      return false;
    }
    //!(tool instanceof LocalInspectionToolWrapper)
    return isToCheckMember(owner, tool.getShortName());
  }

  public static PsiElement getAnnotationMemberSuppressedIn(final PsiModifierListOwner owner, final String inspectionToolID) {
    PsiModifierList modifierList = owner.getModifierList();
    Collection<String> suppressedIds = getInspectionIdsSuppressedInAnnotation(modifierList);
    for (String ids : suppressedIds) {
      if (isInspectionToolIdMentioned(ids, inspectionToolID)) {
        return modifierList.findAnnotation(SUPPRESS_INSPECTIONS_ANNOTATION_NAME);
      }
    }
    return null;
  }

  public static PsiElement getDocCommentToolSuppressedIn(final PsiDocCommentOwner owner, final String inspectionToolID) {
    PsiDocComment docComment = owner.getDocComment();
    if (docComment != null) {
      PsiDocTag inspectionTag = docComment.findTagByName(SUPPRESS_INSPECTIONS_TAG_NAME);
      if (inspectionTag != null && inspectionTag.getValueElement() != null) {
        String valueText = inspectionTag.getValueElement().getText();
        if (isInspectionToolIdMentioned(valueText, inspectionToolID)) {
          return docComment;
        }
      }
    }
    return null;
  }

  static boolean isInspectionToolIdMentioned(String inspectionsList, String inspectionToolID) {
    Iterable<String> ids = StringUtil.tokenize(inspectionsList, "[,]");

    for (@NonNls String id : ids) {
      if (id.equals(inspectionToolID) || id.equals("ALL")) return true;
    }
    return false;
  }

  @NotNull
  static Collection<String> getInspectionIdsSuppressedInAnnotation(final PsiModifierListOwner owner) {
    if (LanguageLevel.JDK_1_5.compareTo(PsiUtil.getLanguageLevel(owner)) > 0) return Collections.emptyList();
    PsiModifierList modifierList = owner.getModifierList();
    return getInspectionIdsSuppressedInAnnotation(modifierList);
  }

  @NotNull
  private static Collection<String> getInspectionIdsSuppressedInAnnotation(final PsiModifierList modifierList) {
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
        if (annotationMemberValue instanceof PsiLiteralExpression) {
          final Object value = ((PsiLiteralExpression)annotationMemberValue).getValue();
          if (value instanceof String) {
            result.add((String)value);
          }
        }
      }
    }
    else if (attributeValue instanceof PsiLiteralExpression) {
      final Object value = ((PsiLiteralExpression)attributeValue).getValue();
      if (value instanceof String) {
        result.add((String)value);
      }
    }
    return result;
  }

  public void ignoreElement(final InspectionTool tool, final PsiElement element) {
    final RefElement refElement = getRefManager().getReference(element);
    final Set<InspectionTool> tools = myTools.get(tool.getShortName());
    if (tools != null){
      for (InspectionTool inspectionTool : tools) {
        inspectionTool.ignoreElement(refElement);
      }
    }
  }

  public UIOptions getUIOptions() {
    return myUIOptions;
  }

  public void setSplitterProportion(final float proportion) {
    myUIOptions.SPLITTER_PROPORTION = proportion;
  }

  public ToggleAction createToggleAutoscrollAction() {
    return myUIOptions.myAutoScrollToSourceHandler.createToggleAction();
  }

  public void installAutoscrollHandler(JTree tree) {
    myUIOptions.myAutoScrollToSourceHandler.install(tree);
  }

  private static class ProgressWrapper extends ProgressIndicatorBase {
    private ProgressIndicator myOriginal;

    public ProgressWrapper(final ProgressIndicator original) {
      myOriginal = original;
    }

    public boolean isCanceled() {
      return myOriginal.isCanceled();
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

  private static int getRequestListSize(HashMap list) {
    if (list == null) return 0;
    return list.size();
  }

  private static List<PsiElement> getSortedIDs(Map<PsiElement, ?> requests) {
    List<PsiElement> result = new ArrayList<PsiElement>();
    for (PsiElement id : requests.keySet()) {
      result.add(id);
    }

    Collections.sort(result, new Comparator<PsiElement>() {
      public int compare(PsiElement o1, PsiElement o2) {
        return o1.getContainingFile().getName().compareTo(o2.getContainingFile().getName());
      }
    });

    return result;
  }

  private PsiReferenceProcessor createReferenceProcessor(final List<UsagesProcessor> processors) {
    return new PsiReferenceProcessor() {
      public boolean execute(PsiReference reference) {
        AnalysisScope scope = getRefManager().getScope();
        if ((scope.contains(reference.getElement()) && reference.getElement().getContainingFile() instanceof PsiJavaFile) ||
            PsiTreeUtil.getParentOfType(reference.getElement(), PsiDocComment.class) != null) {
          return true;
        }
        UsagesProcessor[] processorsArrayed = processors.toArray(new UsagesProcessor[processors.size()]);
        for (UsagesProcessor processor : processorsArrayed) {
          if (!processor.process(reference)) {
            processors.remove(processor);
          }
        }

        return processors.size() > 0;
      }
    };
  }

  private void launchInspections(final AnalysisScope scope, final InspectionManager manager) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      }
    });


    LOG.info("Code inspection started");

    Runnable runInspection = new Runnable() {
      public void run() {
        performInspectionsWithProgress(scope, manager);
      }
    };

    if (!ProgressManager.getInstance()
      .runProcessWithProgressSynchronously(runInspection, InspectionsBundle.message("inspection.progress.title"), true, myProject)) {
      return;
    }

    InspectionResultsView view = new InspectionResultsView(myProject, RUN_WITH_EDITOR_PROFILE ? null : getCurrentProfile(), scope, this);
    if (!view.update() && !getUIOptions().SHOW_ONLY_DIFF) {
      Messages.showMessageDialog(myProject, InspectionsBundle.message("inspection.no.problems.message"),
                                 InspectionsBundle.message("inspection.no.problems.dialog.title"), Messages.getInformationIcon());
      close(true);
    }
    else {
      addView(view);
    }
  }

  private void performInspectionsWithProgress(final AnalysisScope scope, final InspectionManager manager) {
    performInspectionsWithProgress(scope, RUN_WITH_EDITOR_PROFILE, manager);
  }

  private void performInspectionsWithProgress(final AnalysisScope scope,
                                              final boolean runWithEditorSettings,
                                              final InspectionManager manager) {
    try {
      myProgressIndicator = ProgressManager.getInstance().getProgressIndicator();
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          final PsiManager psiManager = PsiManager.getInstance(myProject);
          try {
            psiManager.startBatchFilesProcessingMode();
            //psiManager.performActionWithFormatterDisabled(new Runnable() {
            //  public void run() {
                ((RefManagerImpl)getRefManager()).inspectionReadActionStarted();
                EntryPointsManager.getInstance(getProject()).resolveEntryPoints(getRefManager());
                List<InspectionTool> needRepeatSearchRequest = new ArrayList<InspectionTool>();
                runTools(needRepeatSearchRequest, scope, runWithEditorSettings, manager);
                performPostRunFindUsages(needRepeatSearchRequest, manager);
            //  }
            //});
          }
          catch (ProcessCanceledException e) {
            cleanup();
            throw e;
          }
          finally {
            psiManager.finishBatchFilesProcessingMode();
          }
        }
      });
    }
    finally {
      if (myRefManager != null) {
        ((RefManagerImpl)getRefManager()).inspectionReadActionFinished();
      }
    }
  }

  private void performPostRunFindUsages(List<InspectionTool> needRepeatSearchRequest, final InspectionManager manager) {
    FIND_EXTERNAL_USAGES.setTotalAmount(getRequestCount() * 2);

    do {
      processSearchRequests();
      InspectionTool[] requestors = needRepeatSearchRequest.toArray(new InspectionTool[needRepeatSearchRequest.size()]);
      for (InspectionTool requestor : requestors) {
        if (!requestor.queryExternalUsagesRequests(manager)) needRepeatSearchRequest.remove(requestor);
      }
      int oldSearchRequestCount = FIND_EXTERNAL_USAGES.getTotalAmount();
      float proportion = FIND_EXTERNAL_USAGES.getProgress();
      int totalAmount = oldSearchRequestCount + getRequestCount() * 2;
      FIND_EXTERNAL_USAGES.setTotalAmount(totalAmount);
      FIND_EXTERNAL_USAGES.setDoneAmount((int)(totalAmount * proportion));
    }
    while (needRepeatSearchRequest.size() > 0);
  }

  private void runTools(final List<InspectionTool> needRepeatSearchRequest,
                        final AnalysisScope scope,
                        final boolean runWithEditorSettings,
                        final InspectionManager manager) {
    final HashMap<String, Set<InspectionTool>> usedTools = new HashMap<String, Set<InspectionTool>>();
    final Map<String, Set<InspectionTool>> localTools = new HashMap<String, Set<InspectionTool>>();
    initializeTools(scope, usedTools, localTools, runWithEditorSettings);
    final Set<InspectionTool> currentProfileLocalTools = localTools.get(getCurrentProfile().getName());
    if (runWithEditorSettings || (currentProfileLocalTools != null && currentProfileLocalTools.size() > 0)) {
      final PsiManager psiManager = PsiManager.getInstance(myProject);
      try {
        scope.accept(new PsiRecursiveElementVisitor() {
          public void visitReferenceExpression(PsiReferenceExpression expression) {
          }

          @Override
          public void visitFile(PsiFile file) {
            final InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(myProject);
            InspectionProfile profile;
            if (runWithEditorSettings) {
              profile = profileManager.getInspectionProfile(file);
            }
            else {
              profile = getCurrentProfile();
            }
            final VirtualFile virtualFile = file.getVirtualFile();
            if (virtualFile != null) {
              incrementJobDoneAmount(LOCAL_ANALYSIS, virtualFile.getPresentableUrl());
            }
            final Set<InspectionTool> tools = localTools.get(profile.getName());
            for (InspectionTool tool : tools) {
              ((LocalInspectionToolWrapper)tool).processFile(file, true, manager);
              psiManager.dropResolveCaches();
            }
          }
        });
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
    for (Set<InspectionTool> tools : usedTools.values()) {
      for (InspectionTool tool : tools) {
        if (tool.isGraphNeeded()) {
          ((RefManagerImpl)tool.getRefManager()).findAllDeclarations();
        }
        tool.runInspection(scope, manager);
        if (tool.queryExternalUsagesRequests(manager)) {
          needRepeatSearchRequest.add(tool);
        }
      }
    }
  }

  private void initializeTools(AnalysisScope scope,
                               Map<String, Set<InspectionTool>> tools,
                               Map<String, Set<InspectionTool>> localTools,
                               boolean runWithEditorSettings) {
    myJobDescriptors = new ArrayList<JobDescriptor>();
    final InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(getProject());
    if (runWithEditorSettings) {
      final Set<String> profiles = scope.getActiveInspectionProfiles();
      for (String profile : profiles) {
        InspectionProfileWrapper inspectionProfile = profileManager.getProfileWrapper(profile);
        if (inspectionProfile == null){
          inspectionProfile = new InspectionProfileWrapper((InspectionProfile)profileManager.getProfile(profile));
          inspectionProfile.init(getProject()); //offline inspections only
        }
        processProfileTools(inspectionProfile, tools, localTools);
      }
    }
    else {
      InspectionProfileWrapper profile = new InspectionProfileWrapper(getCurrentProfile());
      processProfileTools(profile, tools, localTools);
      profile.init(getProject());  //offline inspections only
    }

    BUILD_GRAPH.setTotalAmount(scope.getFileCount());
    LOCAL_ANALYSIS.setTotalAmount(scope.getFileCount());
  }

  private void processProfileTools(final InspectionProfileWrapper inspectionProfile,
                                   final Map<String, Set<InspectionTool>> tools,
                                   final Map<String, Set<InspectionTool>> localTools) {
    final InspectionTool[] usedTools = inspectionProfile.getInspectionTools();
    final Set<InspectionTool> profileTools = new HashSet<InspectionTool>();
    tools.put(inspectionProfile.getName(), profileTools);
    final HashSet<InspectionTool> localProfileTools = new HashSet<InspectionTool>();
    localTools.put(inspectionProfile.getName(), localProfileTools);
    for (InspectionTool tool : usedTools) {
      if (inspectionProfile.isToolEnabled(HighlightDisplayKey.find(tool.getShortName()))) {
        tool.initialize(this);
        Set<InspectionTool> sertainTools = myTools.get(tool.getShortName());
        if (sertainTools == null){
          sertainTools = new HashSet<InspectionTool>();
          myTools.put(tool.getShortName(), sertainTools);
        }
        sertainTools.add(tool);
        if (tool instanceof LocalInspectionToolWrapper) {
          localProfileTools.add(tool);
          appendJobDescriptor(LOCAL_ANALYSIS);
        }
        else {
          profileTools.add(tool);
          JobDescriptor[] jobDescriptors = tool.getJobDescriptors();
          for (JobDescriptor jobDescriptor : jobDescriptors) {
            appendJobDescriptor(jobDescriptor);
          }
        }
      }
    }
  }

  private void appendJobDescriptor(JobDescriptor job) {
    if (!myJobDescriptors.contains(job)) {
      myJobDescriptors.add(job);
      job.setDoneAmount(0);
    }
  }

  public void close(boolean noSuspisiousCodeFound) {
    if (!noSuspisiousCodeFound && (myView == null || myView.isRerun())) return;
    final InspectionManagerEx managerEx = ((InspectionManagerEx)InspectionManagerEx.getInstance(myProject));
    managerEx.closeRunningContext(this);
    managerEx.getUIOptions().save(myUIOptions);
    getContentManager().removeContent(myContent);
    for (Set<InspectionTool> tools : myTools.values()) {
      for (InspectionTool tool : tools) {
        tool.finalCleanup();
      }
    }
    cleanup();
    if (myView != null) {
      myView.dispose();
      myView = null;
    }
  }

  public void refreshViews() {
    if (myView != null) {
      myView.updateView(false);
    }
  }

  public void incrementJobDoneAmount(JobDescriptor job, String message) {
    if (myProgressIndicator == null) return;

    ProgressManager.getInstance().checkCanceled();

    int old = job.getDoneAmount();
    job.setDoneAmount(old + 1);

    int jobCount = myJobDescriptors.size();
    float totalProgress = 0;
    for (JobDescriptor jobDescriptor : myJobDescriptors) {
      totalProgress += jobDescriptor.getProgress();
    }

    totalProgress /= jobCount;

    myProgressIndicator.setFraction(totalProgress);
    myProgressIndicator.setText(job.getDisplayName() + " " + message);
  }


  public void setExternalProfile(InspectionProfile profile) {
    myExternalProfile = profile;
  }

  public boolean areResultsShown() {
    return myView != null;
  }
}
