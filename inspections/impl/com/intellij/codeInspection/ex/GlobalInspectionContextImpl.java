/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.ex;

import com.intellij.CommonBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.PerformAnalysisInBackgroundOption;
import com.intellij.analysis.AnalysisUIOptions;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.LocalInspectionsPass;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.deadCode.DeadCodeInspection;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.ide.util.projectWizard.JdkChooserPanel;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
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
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.search.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashMap;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.*;
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

  private THashMap<PsiElement, List<DerivedMethodsProcessor>> myDerivedMethodsRequests;
  private THashMap<PsiElement, List<DerivedClassesProcessor>> myDerivedClassesRequests;
  private THashMap<PsiElement, List<UsagesProcessor>> myMethodUsagesRequests;
  private THashMap<PsiElement, List<UsagesProcessor>> myFieldUsagesRequests;
  private THashMap<PsiElement, List<UsagesProcessor>> myClassUsagesRequests;
  private ProgressIndicator myProgressIndicator;


  public static final JobDescriptor BUILD_GRAPH = new JobDescriptor(InspectionsBundle.message("inspection.processing.job.descriptor"));
  public static final JobDescriptor FIND_EXTERNAL_USAGES =
    new JobDescriptor(InspectionsBundle.message("inspection.processing.job.descriptor1"));
  private static final JobDescriptor LOCAL_ANALYSIS = new JobDescriptor(InspectionsBundle.message("inspection.processing.job.descriptor2"));


  private InspectionProfile myExternalProfile = null;

  public boolean RUN_WITH_EDITOR_PROFILE = false;
  private boolean RUN_GLOBAL_TOOLS_ONLY = false;
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.GlobalInspectionContextImpl");
  @NonNls public static final String SUPPRESS_INSPECTIONS_TAG_NAME = "noinspection";
  public static final String SUPPRESS_INSPECTIONS_ANNOTATION_NAME = "java.lang.SuppressWarnings";
  @NonNls public static final Pattern SUPPRESS_IN_LINE_COMMENT_PATTERN =
    Pattern.compile("//\\s*" + SUPPRESS_INSPECTIONS_TAG_NAME + "\\s+(\\w+(s*,\\w+)*)");

  private Map<String, Set<Pair<InspectionTool, InspectionProfile>>> myTools = new THashMap<String, Set<Pair<InspectionTool, InspectionProfile>>>();

  private AnalysisUIOptions myUIOptions;

  public GlobalInspectionContextImpl(Project project, ContentManager contentManager) {
    myProject = project;

    myUIOptions = AnalysisUIOptions.getInstance(myProject).copy();
    myRefManager = null;
    myCurrentScope = null;
    myContentManager = contentManager;
    if (myContentManager != null) { //test || offline
      myContentManager.addContentManagerListener(new ContentManagerAdapter() {
        public void contentRemoved(ContentManagerEvent event) {
          if (event.getContent() == myContent){
            if (myView != null) {
              close(false);
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

  public InspectionProfile getCurrentProfile() {
    if (myExternalProfile != null) return myExternalProfile;
    InspectionManagerEx managerEx = (InspectionManagerEx)InspectionManager.getInstance(myProject);
    final InspectionProjectProfileManager inspectionProfileManager = InspectionProjectProfileManager.getInstance(myProject);
    Profile profile = inspectionProfileManager.getProfiles().get(managerEx.getCurrentProfile());
    if (profile == null) {
      profile = InspectionProfileManager.getInstance().getProfile(managerEx.getCurrentProfile());
      if (profile != null) return (InspectionProfile)profile;

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
    return entity instanceof RefElement && ((RefElementImpl)entity).isSuppressed(id);
  }

  public boolean shouldCheck(RefEntity entity, GlobalInspectionTool tool) {
    if (entity instanceof RefElement) {
      final RefElementImpl refElement = (RefElementImpl)entity;
      if (refElement.isSuppressed(tool.getShortName())) return false;

      final PsiElement element = refElement.getElement();

      if (element == null) return false;

      if (RUN_WITH_EDITOR_PROFILE) {
        final InspectionProfileEntry inspectionTool =
          InspectionProjectProfileManager.getInstance(element.getProject()).getInspectionProfile(element)
            .getInspectionTool(tool.getShortName());
        if (inspectionTool instanceof GlobalInspectionToolWrapper && ((GlobalInspectionToolWrapper)inspectionTool).getTool() != tool) {
          return false;
        }
      }
    }
    return true;
  }

  public boolean isSuppressed(PsiElement element, String id) {
    final RefManagerImpl refManager = (RefManagerImpl)getRefManager();
    if (refManager.isDeclarationsFound() && element instanceof PsiModifierListOwner) {
      final RefElement refElement = refManager.getReference(element);
      return refElement instanceof RefElementImpl && ((RefElementImpl)refElement).isSuppressed(id);
    }
    return element instanceof PsiDocCommentOwner && !isToCheckMember((PsiDocCommentOwner)element, id);
  }


  public void addView(InspectionResultsView view, String title) {
    myView = view;
    ContentManager contentManager = getContentManager();
    myContent = PeerFactory.getInstance().getContentFactory().createContent(view, title, false);

    myContent.setDisposer(myView);
    contentManager.addContent(myContent);
    contentManager.setSelectedContent(myContent);

    ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.INSPECTION).activate(null);
  }

  private void addView(InspectionResultsView view) {
    addView(view, view.getCurrentProfileName() == null
                  ? InspectionsBundle.message("inspection.results.title")
                  : InspectionsBundle.message("inspection.results.for.profile.toolwindow.title", view.getCurrentProfileName()));

  }


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

    for (Set<Pair<InspectionTool, InspectionProfile>> tools : myTools.values()) {
      for (Pair<InspectionTool, InspectionProfile> pair : tools) {
        pair.first.cleanup();
      }
    }
    myTools.clear();

    //EntryPointsManager.getInstance(getProject()).cleanup();

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
    while (PsiManager.getInstance(getProject()).findClass("java.lang.Object", GlobalSearchScope.allScope(getProject())) == null) {
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
                                       final String outputPath,
                                       final boolean runWithEditorSettings,
                                       final boolean runGlobalToolsOnly,
                                       final InspectionManager manager) {
    cleanup();

    myCurrentScope = scope;

    final boolean oldProfileSetting = RUN_WITH_EDITOR_PROFILE;
    InspectionTool.setOutputPath(outputPath);
    RUN_WITH_EDITOR_PROFILE = runWithEditorSettings;
    final boolean oldToolsSettings = RUN_GLOBAL_TOOLS_ONLY;
    RUN_GLOBAL_TOOLS_ONLY = runGlobalToolsOnly;
    try {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          performInspectionsWithProgress(scope, manager);
          @NonNls final String ext = ".xml";
          for (String toolName : myTools.keySet()) {
            final Element root = new Element(InspectionsBundle.message("inspection.problems"));
            final Document doc = new Document(root);
            final Set<Pair<InspectionTool, InspectionProfile>> sameTools = myTools.get(toolName);
            boolean hasProblems = false;
            boolean isLocalTool = false;
            if (sameTools != null) {
              for (Pair<InspectionTool, InspectionProfile> toolDescr : sameTools) {
                final InspectionTool tool = toolDescr.first;
                if (tool instanceof LocalInspectionToolWrapper) {
                  hasProblems = new File(outputPath, toolName + ext).exists();
                  isLocalTool = true;
                }
                else {
                  tool.updateContent();
                  if (tool.hasReportedProblems()) {
                    hasProblems = true;
                    tool.exportResults(root);
                  }
                }
              }
            }
            if (!hasProblems) continue;
            @NonNls final String isLocalToolAttribute = "is_local_tool";
            root.setAttribute(isLocalToolAttribute, String.valueOf(isLocalTool));
            OutputStream outStream = null;
            try {
              new File(outputPath).mkdirs();
              final File file = new File(outputPath, toolName + ext);
              if (isLocalTool) {
                outStream = localInspectionFilePreparations(file, outStream, isLocalToolAttribute, isLocalTool);
              }
              else {
                PathMacroManager.getInstance(getProject()).collapsePaths(doc.getRootElement());
                outStream = new BufferedOutputStream(new FileOutputStream(file));
                JDOMUtil.writeDocument(doc, outStream, "\n");
              }
            }
            catch (IOException e) {
              LOG.error(e);
            }
            finally {
              if (outStream != null) {
                try {
                  outStream.close();
                }
                catch (IOException e) {
                  LOG.error(e);
                }
              }
            }
          }

        }
      });
    }
    finally {
      InspectionTool.setOutputPath(null);
      RUN_WITH_EDITOR_PROFILE = oldProfileSetting;
      RUN_GLOBAL_TOOLS_ONLY = oldToolsSettings;
    }
  }

  private static OutputStream localInspectionFilePreparations(final File file,
                                                              OutputStream outStream,
                                                              final String localToolAttribute,
                                                              final boolean localTool) throws IOException {
    BufferedReader reader = null;
    try {
      StringBuilder buf = new StringBuilder();
      reader = new BufferedReader(new FileReader(file));
      String line = reader.readLine();
      while (line != null) {
        buf.append(line).append("\n");
        line = reader.readLine();
      }
      outStream = new BufferedOutputStream(new FileOutputStream(file));
      outStream.write(("<" + InspectionsBundle.message("inspection.problems") + " " + localToolAttribute + "=\"" + localTool + "\">").getBytes());
      outStream.write(buf.toString().getBytes());
      outStream.write(("</" + InspectionsBundle.message("inspection.problems") + ">").getBytes());
    }
    finally {
      if (reader != null){
        reader.close();
      }
    }
    return outStream;
  }

  public void processSearchRequests() {
    final PsiManager psiManager = PsiManager.getInstance(getProject());
    final PsiSearchHelper helper = psiManager.getSearchHelper();
    final RefManager refManager = getRefManager();
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
            return !processors.isEmpty();
          }
        }, psiClass, searchScope, false);
      }

      myDerivedClassesRequests = null;
    }

    if (myDerivedMethodsRequests != null) {
      List<PsiElement> sortedIDs = getSortedIDs(myDerivedMethodsRequests);
      for (PsiElement sortedID : sortedIDs) {
        final PsiMethod psiMethod = (PsiMethod)sortedID;
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

            return !processors.isEmpty();
          }
        }, psiMethod, searchScope, true);
      }

      myDerivedMethodsRequests = null;
    }

    if (myFieldUsagesRequests != null) {
      List<PsiElement> sortedIDs = getSortedIDs(myFieldUsagesRequests);
      for (PsiElement sortedID : sortedIDs) {
        final PsiField psiField = (PsiField)sortedID;
        final List<UsagesProcessor> processors = myFieldUsagesRequests.get(psiField);

        incrementJobDoneAmount(FIND_EXTERNAL_USAGES, RefUtil.getInstance().getQualifiedName(refManager.getReference(psiField)));

        helper.processReferences(createReferenceProcessor(processors), psiField, searchScope, false);
      }

      myFieldUsagesRequests = null;
    }

    if (myClassUsagesRequests != null) {
      List<PsiElement> sortedIDs = getSortedIDs(myClassUsagesRequests);
      for (PsiElement sortedID : sortedIDs) {
        final PsiClass psiClass = (PsiClass)sortedID;
        final List<UsagesProcessor> processors = myClassUsagesRequests.get(psiClass);

        incrementJobDoneAmount(FIND_EXTERNAL_USAGES, psiClass.getQualifiedName());

        helper.processReferences(createReferenceProcessor(processors), psiClass, searchScope, false);
      }

      myClassUsagesRequests = null;
    }

    if (myMethodUsagesRequests != null) {
      List<PsiElement> sortedIDs = getSortedIDs(myMethodUsagesRequests);
      for (PsiElement sortedID : sortedIDs) {
        final PsiMethod psiMethod = (PsiMethod)sortedID;
        final List<UsagesProcessor> processors = myMethodUsagesRequests.get(psiMethod);

        incrementJobDoneAmount(FIND_EXTERNAL_USAGES, RefUtil.getInstance().getQualifiedName(refManager.getReference(psiMethod)));

        helper.processReferencesIncludingOverriding(createReferenceProcessor(processors), psiMethod, searchScope);
      }

      myMethodUsagesRequests = null;
    }
  }

  private static boolean isToCheckMember(PsiDocCommentOwner owner, @NonNls String inspectionToolID) {
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

      classContainer = classContainer.getContainingClass();
    }
    return null;
  }

  @SuppressWarnings({"SimplifiableIfStatement"})
  public boolean isToCheckMember(RefElement owner, InspectionTool tool) {
    final PsiElement element = owner.getElement();
    if (RUN_WITH_EDITOR_PROFILE && InspectionProjectProfileManager.getInstance(element.getProject()).getInspectionProfile(element)
      .getInspectionTool(tool.getShortName()) != tool) {
      return false;
    }
    return !((RefElementImpl)owner).isSuppressed(tool.getShortName());
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
  private static PsiElement getDocCommentToolSuppressedIn(final PsiDocCommentOwner owner, final String inspectionToolID) {
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
    final Set<Pair<InspectionTool, InspectionProfile>> tools = myTools.get(tool.getShortName());
    if (tools != null){
      for (Pair<InspectionTool, InspectionProfile> inspectionTool : tools) {
        ignoreElementRecursively(inspectionTool.first, refElement);
      }
    }
  }

  private static void ignoreElementRecursively(final InspectionTool tool, final RefEntity refElement) {
    if (refElement != null) {
      tool.ignoreCurrentElement(refElement);
      final List<RefEntity> children = refElement.getChildren();
      if (children != null) {
        for (RefEntity child : children) {
          ignoreElementRecursively(tool, child);
        }
      }
    }
  }

  public AnalysisUIOptions getUIOptions() {
    return myUIOptions;
  }

  public void setSplitterProportion(final float proportion) {
    myUIOptions.SPLITTER_PROPORTION = proportion;
  }

  public ToggleAction createToggleAutoscrollAction() {
    return myUIOptions.getAutoScrollToSourceHandler().createToggleAction();
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

  private static int getRequestListSize(THashMap list) {
    if (list == null) return 0;
    return list.size();
  }

  private static List<PsiElement> getSortedIDs(Map<PsiElement, ?> requests) {
    final List<PsiElement> result = new ArrayList<PsiElement>();
    for (PsiElement id : requests.keySet()) {
      result.add(id);
    }

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
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

  private PsiReferenceProcessor createReferenceProcessor(final List<UsagesProcessor> processors) {
    return new PsiReferenceProcessor() {
      public boolean execute(PsiReference reference) {
        AnalysisScope scope = getRefManager().getScope();
        if ((scope.contains(reference.getElement()) && reference.getElement().getContainingFile() instanceof PsiJavaFile) ||
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


    final Runnable successRunnable = new Runnable() {
      public void run() {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            LOG.info("Code inspection finished");

            final InspectionResultsView view = new InspectionResultsView(myProject, RUN_WITH_EDITOR_PROFILE ? null : getCurrentProfile(),
                                                                         scope, GlobalInspectionContextImpl.this,
                                                                         new InspectionRVContentProviderImpl(myProject));
            if (!view.update() && !getUIOptions().SHOW_ONLY_DIFF) {
              Messages.showMessageDialog(myProject, InspectionsBundle.message("inspection.no.problems.message"),
                                         InspectionsBundle.message("inspection.no.problems.dialog.title"), Messages.getInformationIcon());
              close(true);
            }
            else {
              addView(view);
            }
          }
        });
      }
    };
    ProgressManager.getInstance()
      .runProcessWithProgressAsynchronously(myProject, InspectionsBundle.message("inspection.progress.title"), runInspection,
                                            successRunnable, null, new PerformAnalysisInBackgroundOption(myProject));

  }

  private void performInspectionsWithProgress(final AnalysisScope scope, final InspectionManager manager) {
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    myProgressIndicator = ProgressManager.getInstance().getProgressIndicator();
    //init manager in read action
    RefManagerImpl refManager = (RefManagerImpl)ApplicationManager.getApplication().runReadAction(new Computable<RefManager>() {
      public RefManager compute() {
        return getRefManager();
      }
    });
    try {
      psiManager.startBatchFilesProcessingMode();
      refManager.inspectionReadActionStarted();
      refManager.getEntryPointsManager().resolveEntryPoints(refManager);
      BUILD_GRAPH.setTotalAmount(scope.getFileCount());
      LOCAL_ANALYSIS.setTotalAmount(scope.getFileCount());
      final List<InspectionTool> needRepeatSearchRequest = new ArrayList<InspectionTool>();
      ((ProgressManagerImpl)ProgressManager.getInstance())
        .executeProcessUnderProgress(new Runnable() {  //to override current progress in order to hide useless messages/%
          public void run() {
            runTools(needRepeatSearchRequest, scope, manager);
          }
        }, myProgressIndicator != null ? new ProgressWrapper(myProgressIndicator) : null);
    }
    catch (ProcessCanceledException e) {
      cleanup((InspectionManagerEx)manager);
      throw e;
    }
    catch (Exception e) {
      LOG.error(e);
    }
    finally {
      refManager.inspectionReadActionFinished();
      psiManager.finishBatchFilesProcessingMode();
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
    while (!needRepeatSearchRequest.isEmpty());
  }

  private void runTools(final List<InspectionTool> needRepeatSearchRequest, final AnalysisScope scope, final InspectionManager manager) {
    final HashMap<String, Set<InspectionTool>> usedTools = new HashMap<String, Set<InspectionTool>>();
    final Map<String, Set<InspectionTool>> localTools = new HashMap<String, Set<InspectionTool>>();
    initializeTools(scope, usedTools, localTools);
    ((RefManagerImpl)getRefManager()).initializeAnnotators();
    for (Set<InspectionTool> tools : usedTools.values()) {
      for (InspectionTool tool : tools) {
        try {
          if (tool.isGraphNeeded()) {
            ((RefManagerImpl)tool.getRefManager()).findAllDeclarations();
          }
          tool.runInspection(scope, manager);
          if (tool.queryExternalUsagesRequests(manager)) {
            needRepeatSearchRequest.add(tool);
          }
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }
    performPostRunFindUsages(needRepeatSearchRequest, manager);
    if (RUN_GLOBAL_TOOLS_ONLY) return;
    final Set<InspectionTool> currentProfileLocalTools = localTools.get(getCurrentProfile().getName());
    if (RUN_WITH_EDITOR_PROFILE || currentProfileLocalTools != null && !currentProfileLocalTools.isEmpty()) {
      final PsiManager psiManager = PsiManager.getInstance(myProject);
      final InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(myProject);
      scope.accept(new PsiRecursiveElementVisitor() {
        public void visitReferenceExpression(PsiReferenceExpression expression) {
        }

        public void visitJspFile(final JspFile file) {
          visitFile(file);
        }

        @Override
        public void visitFile(PsiFile file) {
          InspectionProfile profile;
          if (RUN_WITH_EDITOR_PROFILE) {
            profile = profileManager.getInspectionProfile(file);
          }
          else {
            profile = getCurrentProfile();
          }
          final VirtualFile virtualFile = file.getVirtualFile();
          if (virtualFile != null) {
            incrementJobDoneAmount(LOCAL_ANALYSIS, VfsUtil.calcRelativeToProjectPath(virtualFile, myProject));
          }
          final Set<InspectionTool> tools = localTools.get(profile.getName());
          final FileViewProvider viewProvider = psiManager.findViewProvider(virtualFile);
          final com.intellij.openapi.editor.Document document = viewProvider != null ? viewProvider.getDocument() : null;
          if (document == null) return; //do not inspect binary files
          final LocalInspectionsPass pass = new LocalInspectionsPass(file, document, 0, file.getTextLength());
          try {
            pass.doInspectInBatch((InspectionManagerEx)manager, tools.toArray(new InspectionTool[tools.size()]), myProgressIndicator);
          }
          catch (ProcessCanceledException e) {
            throw e;
          }
          catch (Exception e) {
            LOG.error(e);
          }
        }
      });
    }
  }

  public void initializeTools(AnalysisScope scope, Map<String, Set<InspectionTool>> tools, Map<String, Set<InspectionTool>> localTools) {
    myJobDescriptors = new ArrayList<JobDescriptor>();
    final InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(getProject());
    if (RUN_WITH_EDITOR_PROFILE) {
      final Set<String> profiles = scope.getActiveInspectionProfiles();
      for (String profile : profiles) {
        InspectionProfileWrapper inspectionProfile = profileManager.getProfileWrapper(profile);
        if (inspectionProfile == null){
          inspectionProfile = new InspectionProfileWrapper((InspectionProfile)profileManager.getProfile(profile));
          final InspectionProfileWrapper profileWrapper = inspectionProfile;
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              profileWrapper.init(getProject()); //offline inspections only
            }
          });
        }
        processProfileTools(inspectionProfile, tools, localTools);
      }
    }
    else {
      final InspectionProfileWrapper profile = new InspectionProfileWrapper(getCurrentProfile());
      processProfileTools(profile, tools, localTools);
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          profile.init(getProject());  //offline inspections only
        }
      });
    }
  }

  private void processProfileTools(final InspectionProfileWrapper inspectionProfile,
                                   final Map<String, Set<InspectionTool>> tools,
                                   final Map<String, Set<InspectionTool>> localTools) {
    inspectionProfile.getInspectionProfile().cleanup();
    final InspectionTool[] usedTools = inspectionProfile.getInspectionTools();
    final Set<InspectionTool> profileTools = new TreeSet<InspectionTool>(new Comparator<InspectionTool>() {
      public int compare(final InspectionTool tool1, final InspectionTool tool2) {
        if (tool1.getShortName().equals(DeadCodeInspection.SHORT_NAME)) return -1;
        if (tool2.getShortName().equals(DeadCodeInspection.SHORT_NAME)) return 1;
        return tool1.getShortName().compareTo(tool2.getShortName());
      }
    });
    tools.put(inspectionProfile.getName(), profileTools);
    final HashSet<InspectionTool> localProfileTools = new HashSet<InspectionTool>();
    localTools.put(inspectionProfile.getName(), localProfileTools);
    for (InspectionTool tool : usedTools) {
      final String shortName = tool.getShortName();
      final HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
      if (inspectionProfile.isToolEnabled(key)) {
        tool.initialize(this);
        Set<Pair<InspectionTool, InspectionProfile>> sertainTools = myTools.get(shortName);
        if (sertainTools == null){
          sertainTools = new HashSet<Pair<InspectionTool, InspectionProfile>>();
          myTools.put(shortName, sertainTools);
        }
        sertainTools.add(Pair.create(tool, inspectionProfile.getInspectionProfile()));
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

  public Map<String, Set<Pair<InspectionTool, InspectionProfile>>> getTools() {
    return myTools;
  }

  private void appendJobDescriptor(JobDescriptor job) {
    if (!myJobDescriptors.contains(job)) {
      myJobDescriptors.add(job);
      job.setDoneAmount(0);
    }
  }

  public void close(boolean noSuspisiousCodeFound) {
    if (!noSuspisiousCodeFound && (myView == null || myView.isRerun())) return;
    final InspectionManagerEx managerEx = (InspectionManagerEx)InspectionManager.getInstance(myProject);
    cleanup(managerEx);
    AnalysisUIOptions.getInstance(myProject).save(myUIOptions);
    final ContentManager contentManager = getContentManager();
    if (contentManager != null) {  //null for tests
      contentManager.removeContent(myContent);
    }
    myView = null;
  }

  public void cleanup(final InspectionManagerEx managerEx) {
    managerEx.closeRunningContext(this);
    for (Set<Pair<InspectionTool, InspectionProfile>> tools : myTools.values()) {
      for (Pair<InspectionTool, InspectionProfile> tool : tools) {
        tool.first.finalCleanup();
      }
    }
    cleanup();
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
}
