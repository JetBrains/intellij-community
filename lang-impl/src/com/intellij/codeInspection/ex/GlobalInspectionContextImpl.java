/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.ex;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisUIOptions;
import com.intellij.analysis.PerformAnalysisInBackgroundOption;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.LocalInspectionsPass;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.lang.GlobalInspectionContextExtension;
import com.intellij.codeInspection.lang.InspectionExtensionsFactory;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.profile.Profile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.ui.content.*;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashMap;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.*;
import java.util.*;

public class GlobalInspectionContextImpl implements GlobalInspectionContext {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.GlobalInspectionContextImpl");

  private RefManager myRefManager;
  private final ContentManager myContentManager;

  private AnalysisScope myCurrentScope;
  private final Project myProject;
  private List<JobDescriptor> myJobDescriptors;
  private InspectionResultsView myView = null;

  private Content myContent = null;


  private ProgressIndicator myProgressIndicator;
  public static final JobDescriptor BUILD_GRAPH = new JobDescriptor(InspectionsBundle.message("inspection.processing.job.descriptor"));
  public static final JobDescriptor FIND_EXTERNAL_USAGES =
    new JobDescriptor(InspectionsBundle.message("inspection.processing.job.descriptor1"));


  private static final JobDescriptor LOCAL_ANALYSIS = new JobDescriptor(InspectionsBundle.message("inspection.processing.job.descriptor2"));

  private InspectionProfile myExternalProfile = null;

  private Map<Key, GlobalInspectionContextExtension> myExtensions = new HashMap<Key, GlobalInspectionContextExtension>();
  public boolean RUN_WITH_EDITOR_PROFILE = false;
  private boolean RUN_GLOBAL_TOOLS_ONLY = false;

  private final Map<String, Set<Pair<InspectionTool, InspectionProfile>>> myTools = new THashMap<String, Set<Pair<InspectionTool, InspectionProfile>>>();

  private final AnalysisUIOptions myUIOptions;

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
    for (InspectionExtensionsFactory factory : Extensions.getExtensions(InspectionExtensionsFactory.EP_NAME)) {
      final GlobalInspectionContextExtension extension = factory.createGlobalInspectionContextExtension();
      myExtensions.put(extension.getID(), extension);
    }
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public <T> T getExtension(final Key<T> key) {
    return (T)myExtensions.get(key);
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
        final InspectionProfile inspectionProfile =
          InspectionProjectProfileManager.getInstance(element.getProject()).getInspectionProfile(element);
        final Set<Pair<InspectionTool, InspectionProfile>> tools = myTools.get(tool.getShortName());
        for (Pair<InspectionTool, InspectionProfile> inspectionProfilePair : tools) {
          if (Comparing.strEqual(inspectionProfilePair.second.getName(), inspectionProfile.getName())) {
            return ((GlobalInspectionToolWrapper)inspectionProfilePair.first).getTool() == tool;
          }
        }
        return false;
      }
    }
    return true;
  }

  public boolean isSuppressed(PsiElement element, String id) {
    final RefManagerImpl refManager = (RefManagerImpl)getRefManager();
    if (refManager.isDeclarationsFound()) {
      final RefElement refElement = refManager.getReference(element);
      return refElement instanceof RefElementImpl && ((RefElementImpl)refElement).isSuppressed(id);
    }
    return InspectionManagerEx.isSuppressed(element, id);
  }


  public void addView(InspectionResultsView view, String title) {
    myView = view;
    ContentManager contentManager = getContentManager();
    myContent = ContentFactory.SERVICE.getInstance().createContent(view, title, false);

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

  private void cleanup() {
    myProgressIndicator = null;

    for (GlobalInspectionContextExtension extension : myExtensions.values()) {
      extension.cleanup();
    }

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
    if (!InspectionManagerEx.canRunInspections(myProject, true)) return;

    cleanup();
    getContentManager().removeContent(myContent, true);

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
      myRefManager = ApplicationManager.getApplication().runReadAction(new Computable<RefManagerImpl>() {
        public RefManagerImpl compute() {
          return new RefManagerImpl(myProject, myCurrentScope, GlobalInspectionContextImpl.this);
        }
      });
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


  public boolean isToCheckMember(@NotNull RefElement owner, InspectionTool tool) {
    final PsiElement element = owner.getElement();
    return isToCheckMember(element, tool) && !((RefElementImpl)owner).isSuppressed(tool.getShortName());
  }

  public boolean isToCheckMember(final PsiElement element, final InspectionTool tool) {
    if (RUN_WITH_EDITOR_PROFILE) {
      final InspectionProfile inspectionProfile =
        InspectionProjectProfileManager.getInstance(element.getProject()).getInspectionProfile(element);
      final Set<Pair<InspectionTool, InspectionProfile>> tools = myTools.get(tool.getShortName());
      for (Pair<InspectionTool, InspectionProfile> inspectionProfilePair : tools) {
        if (Comparing.strEqual(inspectionProfilePair.second.getName(), inspectionProfile.getName())) {
          return inspectionProfilePair.first == tool;
        }
      }
      return false;
    }
    return true;
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
    RefManagerImpl refManager = (RefManagerImpl)getRefManager();
    try {
      psiManager.startBatchFilesProcessingMode();
      refManager.inspectionReadActionStarted();
      refManager.getEntryPointsManager().resolveEntryPoints(refManager);
      BUILD_GRAPH.setTotalAmount(scope.getFileCount());
      LOCAL_ANALYSIS.setTotalAmount(scope.getFileCount());
      final List<InspectionProfileEntry> needRepeatSearchRequest = new ArrayList<InspectionProfileEntry>();
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

  private void runTools(final List<InspectionProfileEntry> needRepeatSearchRequest, final AnalysisScope scope, final InspectionManager manager) {
    final HashMap<String, List<InspectionProfileEntry>> usedTools = new HashMap<String, List<InspectionProfileEntry>>();
    final Map<String, List<InspectionProfileEntry>> localTools = new HashMap<String, List<InspectionProfileEntry>>();
    initializeTools(scope, usedTools, localTools);
    ((RefManagerImpl)getRefManager()).initializeAnnotators();
    for (List<InspectionProfileEntry> tools : usedTools.values()) {
      for (InspectionProfileEntry entry : tools) {
        final InspectionTool tool = (InspectionTool)entry;
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
    for (GlobalInspectionContextExtension extension : myExtensions.values()) {
      extension.performPostRunActivities(needRepeatSearchRequest, this);
    }
    if (RUN_GLOBAL_TOOLS_ONLY) return;
    final List<InspectionProfileEntry> currentProfileLocalTools = localTools.get(getCurrentProfile().getName());
    if (RUN_WITH_EDITOR_PROFILE || currentProfileLocalTools != null && !currentProfileLocalTools.isEmpty()) {
      final PsiManager psiManager = PsiManager.getInstance(myProject);
      final InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(myProject);
      scope.accept(new PsiRecursiveElementVisitor() {
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
            incrementJobDoneAmount(LOCAL_ANALYSIS, ProjectUtil.calcRelativeToProjectPath(virtualFile, myProject));
          }
          final List<InspectionProfileEntry> tools = localTools.get(profile.getName());
          final FileViewProvider viewProvider = psiManager.findViewProvider(virtualFile);
          final com.intellij.openapi.editor.Document document = viewProvider != null ? viewProvider.getDocument() : null;
          if (document == null) return; //do not inspect binary files
          final LocalInspectionsPass pass = new LocalInspectionsPass(file, document, 0, file.getTextLength());
          try {
            pass.doInspectInBatch((InspectionManagerEx)manager, tools.toArray(new InspectionProfileEntry[tools.size()]), myProgressIndicator);
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

  public void initializeTools(AnalysisScope scope, Map<String, List<InspectionProfileEntry>> tools, Map<String, List<InspectionProfileEntry>> localTools) {
    myJobDescriptors = new ArrayList<JobDescriptor>();
    final InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(getProject());
    if (RUN_WITH_EDITOR_PROFILE) {
      final Set<String> profiles = scope.getActiveInspectionProfiles();
      for (String profileName : profiles) {
        initializeTools(tools, localTools, profileManager, (InspectionProfile)profileManager.getProfile(profileName));
      }
    }
    else {
      initializeTools(tools, localTools, profileManager, getCurrentProfile());
    }
  }

  private void initializeTools(final Map<String, List<InspectionProfileEntry>> tools,
                              final Map<String, List<InspectionProfileEntry>> localTools,
                              final InspectionProjectProfileManager profileManager, final InspectionProfile profile) {
    InspectionProfileWrapper inspectionProfile = profileManager.getProfileWrapper(profile.getName());
    if (inspectionProfile == null){
      inspectionProfile = new InspectionProfileWrapper(profile);
      final InspectionProfileWrapper profileWrapper = inspectionProfile;
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          profileWrapper.init(getProject()); //offline inspections only
        }
      });
    }
    processProfileTools(inspectionProfile, tools, localTools);
  }

  private void processProfileTools(final InspectionProfileWrapper inspectionProfile,
                                   final Map<String, List<InspectionProfileEntry>> tools,
                                   final Map<String, List<InspectionProfileEntry>> localTools) {
    final InspectionProfileEntry[] usedTools = inspectionProfile.getInspectionProfile().getModifiableModel().getInspectionTools();
    final List<InspectionProfileEntry> profileTools = new ArrayList<InspectionProfileEntry>();
    tools.put(inspectionProfile.getName(), profileTools);
    final List<InspectionProfileEntry> localProfileTools = new ArrayList<InspectionProfileEntry>();
    localTools.put(inspectionProfile.getName(), localProfileTools);
    for (InspectionProfileEntry entry : usedTools) {
      final InspectionTool tool = (InspectionTool)entry;
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
    for (GlobalInspectionContextExtension extension : myExtensions.values()) {
      extension.performPreRunActivities(profileTools, localProfileTools);
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
      contentManager.removeContent(myContent, true);
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
