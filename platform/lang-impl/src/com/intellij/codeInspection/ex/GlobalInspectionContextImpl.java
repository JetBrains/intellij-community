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

package com.intellij.codeInspection.ex;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisUIOptions;
import com.intellij.analysis.PerformAnalysisInBackgroundOption;
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
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.profile.Profile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.ui.content.*;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashMap;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GlobalInspectionContextImpl implements GlobalInspectionContext {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.GlobalInspectionContextImpl");

  private RefManager myRefManager;
  private final NotNullLazyValue<ContentManager> myContentManager;

  private AnalysisScope myCurrentScope;
  private final Project myProject;
  private List<JobDescriptor> myJobDescriptors;
  private InspectionResultsView myView = null;

  private Content myContent = null;


  private ProgressIndicator myProgressIndicator;
  public static final JobDescriptor BUILD_GRAPH = new JobDescriptor(InspectionsBundle.message("inspection.processing.job.descriptor"));
  public static final JobDescriptor[] BUILD_GRAPH_ONLY = new JobDescriptor[]{BUILD_GRAPH};
  public static final JobDescriptor FIND_EXTERNAL_USAGES =
    new JobDescriptor(InspectionsBundle.message("inspection.processing.job.descriptor1"));


  private static final JobDescriptor LOCAL_ANALYSIS = new JobDescriptor(InspectionsBundle.message("inspection.processing.job.descriptor2"));

  private InspectionProfile myExternalProfile = null;

  private final Map<Key, GlobalInspectionContextExtension> myExtensions = new HashMap<Key, GlobalInspectionContextExtension>();
  private boolean RUN_GLOBAL_TOOLS_ONLY = false;

  private final Map<String, Tools> myTools = new THashMap<String, Tools>();

  private final AnalysisUIOptions myUIOptions;

  public GlobalInspectionContextImpl(Project project, NotNullLazyValue<ContentManager> contentManager) {
    myProject = project;

    myUIOptions = AnalysisUIOptions.getInstance(myProject).copy();
    myRefManager = null;
    myCurrentScope = null;
    myContentManager = contentManager;
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
    return myContentManager.getValue();
  }

  public InspectionProfile getCurrentProfile() {
    if (myExternalProfile != null) return myExternalProfile;
    InspectionManagerEx managerEx = (InspectionManagerEx)InspectionManager.getInstance(myProject);
    final InspectionProjectProfileManager inspectionProfileManager = InspectionProjectProfileManager.getInstance(myProject);
    Profile profile = inspectionProfileManager.getProfile(managerEx.getCurrentProfile(), false);
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
    return entity instanceof RefElementImpl && ((RefElementImpl)entity).isSuppressed(id);
  }

  public boolean shouldCheck(RefEntity entity, GlobalInspectionTool tool) {
    if (entity instanceof RefElementImpl) {
      final RefElementImpl refElement = (RefElementImpl)entity;
      if (refElement.isSuppressed(tool.getShortName())) return false;

      final PsiFile file = refElement.getContainingFile();

      if (file == null) return false;

      final Tools tools = myTools.get(tool.getShortName());
      for (ScopeToolState state : tools.getTools()) {
        final NamedScope namedScope = state.getScope();
        if (namedScope == null || namedScope.getValue().contains(file, getCurrentProfile().getProfileManager().getScopesManager())) {
          return state.isEnabled() && ((GlobalInspectionToolWrapper)state.getTool()).getTool() == tool;
        }
      }
      return false;
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
    myContentManager.getValue().addContentManagerListener(new ContentManagerAdapter() {
      public void contentRemoved(ContentManagerEvent event) {
        if (event.getContent() == myContent){
          if (myView != null) {
            close(false);
          }
          myContent = null;
        }
      }
    });

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

    for (Tools tools : myTools.values()) {
      for (ScopeToolState state : tools.getTools()) {
        ((InspectionTool)state.getTool()).cleanup();
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
    if (myContent != null) {
      getContentManager().removeContent(myContent, true);
    }

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

    InspectionTool.setOutputPath(outputPath);
    final boolean oldToolsSettings = RUN_GLOBAL_TOOLS_ONLY;
    RUN_GLOBAL_TOOLS_ONLY = runGlobalToolsOnly;
    try {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          performInspectionsWithProgress(scope, manager);
          @NonNls final String ext = ".xml";
          for (Map.Entry<String,Tools> stringSetEntry : myTools.entrySet()) {
            final Element root = new Element(InspectionsBundle.message("inspection.problems"));
            final Document doc = new Document(root);
            final Tools sameTools = stringSetEntry.getValue();
            boolean hasProblems = false;
            boolean isLocalTool = false;
            String toolName = stringSetEntry.getKey();
            if (sameTools != null) {
              for (ScopeToolState toolDescr : sameTools.getTools()) {
                final InspectionTool tool = (InspectionTool)toolDescr.getTool();
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
                outStream = new BufferedOutputStream(new FileOutputStream(file, true));
                outStream.write(("</" + InspectionsBundle.message("inspection.problems") + ">").getBytes());
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
      RUN_GLOBAL_TOOLS_ONLY = oldToolsSettings;
    }
  }


  public boolean isToCheckMember(@NotNull RefElement owner, InspectionTool tool) {
    final PsiElement element = owner.getElement();
    return isToCheckMember(element, tool) && !((RefElementImpl)owner).isSuppressed(tool.getShortName());
  }

  public boolean isToCheckMember(final PsiElement element, final InspectionTool tool) {
    if (true) {
      final Tools tools = myTools.get(tool.getShortName());
      for (ScopeToolState state : tools.getTools()) {
        final NamedScope namedScope = state.getScope();
        if (namedScope == null || namedScope.getValue().contains(element.getContainingFile(), getCurrentProfile().getProfileManager().getScopesManager())) {
          return state.isEnabled() && state.getTool() == tool;
        }
      }
      return false;
    }
    return true;
  }

  public void ignoreElement(final InspectionTool tool, final PsiElement element) {
    final RefElement refElement = getRefManager().getReference(element);
    final Tools tools = myTools.get(tool.getShortName());
    if (tools != null){
      for (ScopeToolState state : tools.getTools()) {
        ignoreElementRecursively((InspectionTool)state.getTool(), refElement);
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

    ProgressManager.getInstance().run(new Task.Backgroundable(getProject(), InspectionsBundle.message("inspection.progress.title"), true, new PerformAnalysisInBackgroundOption(myProject)) {
      public void run(@NotNull ProgressIndicator indicator) {
        performInspectionsWithProgress(scope, manager);
      }

      @Override
      public void onSuccess() {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            LOG.info("Code inspection finished");

            InspectionResultsView view = new InspectionResultsView(myProject, getCurrentProfile(),
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
    });
  }

  private void performInspectionsWithProgress(final AnalysisScope scope, final InspectionManager manager) {
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    myProgressIndicator = ProgressManager.getInstance().getProgressIndicator();
    //init manager in read action
    RefManagerImpl refManager = (RefManagerImpl)getRefManager();
    try {
      psiManager.startBatchFilesProcessingMode();
      refManager.inspectionReadActionStarted();
      BUILD_GRAPH.setTotalAmount(scope.getFileCount());
      LOCAL_ANALYSIS.setTotalAmount(scope.getFileCount());
      final List<InspectionProfileEntry> needRepeatSearchRequest = new ArrayList<InspectionProfileEntry>();
      ((ProgressManagerImpl)ProgressManager.getInstance())
        .executeProcessUnderProgress(new Runnable() {  //to override current progress in order to hide useless messages/%
          public void run() {
            runTools(needRepeatSearchRequest, scope, manager);
          }
        }, ProgressWrapper.wrap(myProgressIndicator));
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
    final List<Tools> globalTools = new ArrayList<Tools>();
    final List<Tools> localTools = new ArrayList<Tools>();
    initializeTools(globalTools, localTools);
    ((RefManagerImpl)getRefManager()).initializeAnnotators();
    for (Tools tools : globalTools) {
      for (ScopeToolState state : tools.getTools()) {
        final InspectionTool tool = (InspectionTool)state.getTool();
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
      try {
        extension.performPostRunActivities(needRepeatSearchRequest, this);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    if (RUN_GLOBAL_TOOLS_ONLY) return;

    final PsiManager psiManager = PsiManager.getInstance(myProject);
    scope.accept(new PsiRecursiveElementVisitor() {
      @Override
      public void visitFile(PsiFile file) {
        final VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile != null) {
          incrementJobDoneAmount(LOCAL_ANALYSIS, ProjectUtil.calcRelativeToProjectPath(virtualFile, myProject));
        }

        final FileViewProvider viewProvider = psiManager.findViewProvider(virtualFile);
        final com.intellij.openapi.editor.Document document = viewProvider == null ? null : viewProvider.getDocument();
        if (document == null || virtualFile.getFileType().isBinary()) return; //do not inspect binary files
        final LocalInspectionsPass pass = new LocalInspectionsPass(file, document, 0, file.getTextLength());
        try {
          final List<InspectionProfileEntry> lTools = new ArrayList<InspectionProfileEntry>();
          for (Tools tool : localTools) {
            final InspectionTool enabledTool = (InspectionTool)tool.getEnabledTool(file);
            if (enabledTool != null) {
              lTools.add(enabledTool);
            }
          }
          pass.doInspectInBatch((InspectionManagerEx)manager, lTools, true);
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

  public void initializeTools(List<Tools> globalTools, List<Tools> localTools) {
    myJobDescriptors = new ArrayList<JobDescriptor>();
    final InspectionProfileImpl profile = new InspectionProfileImpl((InspectionProfileImpl)getCurrentProfile());
    final List<ToolsImpl> usedTools = profile.getAllEnabledInspectionTools();
    for (Tools currentTools : usedTools) {
      final String shortName = currentTools.getShortName();
      myTools.put(shortName, currentTools);
      final InspectionTool tool = (InspectionTool)currentTools.getTool();
      if (tool instanceof LocalInspectionToolWrapper) {
        localTools.add(currentTools);
        appendJobDescriptor(LOCAL_ANALYSIS);
      }
      else {
        globalTools.add(currentTools);
        JobDescriptor[] jobDescriptors = tool.getJobDescriptors();
        for (JobDescriptor jobDescriptor : jobDescriptors) {
          appendJobDescriptor(jobDescriptor);
        }
      }

      for (ScopeToolState state : currentTools.getTools()) {
        ((InspectionTool)state.getTool()).initialize(this);
      }
    }
    for (GlobalInspectionContextExtension extension : myExtensions.values()) {
      extension.performPreRunActivities(globalTools, localTools, this);
    }
  }

  public Map<String, Tools> getTools() {
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
    if (myContent != null) {
      final ContentManager contentManager = getContentManager();
      if (contentManager != null) {  //null for tests
        contentManager.removeContent(myContent, true);
      }
    }
    myView = null;
  }

  public void cleanup(final InspectionManagerEx managerEx) {
    managerEx.closeRunningContext(this);
    for (Tools tools : myTools.values()) {
      for (ScopeToolState state : tools.getTools()) {
        ((InspectionTool)state.getTool()).finalCleanup();
      }
    }
    cleanup();
  }

  public void refreshViews() {
    if (myView != null) {
      myView.updateView(false);
    }
  }

  @Override
  public void incrementJobDoneAmount(JobDescriptor job, String message) {
    if (myProgressIndicator == null) return;

    ProgressManager.checkCanceled();

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
