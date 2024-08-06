// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.JavaCodeInsightTestCase;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.lang.ExternalAnnotatorsFilter;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.search.IndexPatternBuilder;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.xml.XmlFileNSInfoProvider;
import com.intellij.testFramework.*;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlSchemaProvider;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class DaemonAnalyzerTestCase extends JavaCodeInsightTestCase {
  private VirtualFileFilter myVirtualFileFilter = new FileTreeAccessFilter();

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final LocalInspectionTool[] tools = configureLocalInspectionTools();

    InspectionsKt.configureInspections(tools, getProject(), getTestRootDisposable());

    DaemonCodeAnalyzerImpl daemonCodeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject());
    daemonCodeAnalyzer.prepareForTest();
    DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(false);

    if (isStressTest()) {
      IntentionManager.getInstance().getAvailableIntentions();  // hack to avoid slowdowns in PyExtensionFactory
      PathManagerEx.getTestDataPath(); // to cache stuff
      ReferenceProvidersRegistry.getInstance(); // pre-load tons of classes
      InjectedLanguageManager.getInstance(getProject()); // zillion of Dom Sem classes
      LanguageAnnotators.INSTANCE.allForLanguage(JavaLanguage.INSTANCE); // pile of annotator classes loads
      LanguageAnnotators.INSTANCE.allForLanguage(XMLLanguage.INSTANCE);
      ProblemHighlightFilter.EP_NAME.getExtensions();
      ImplicitUsageProvider.EP_NAME.getExtensionList();
      XmlSchemaProvider.EP_NAME.getExtensionList();
      XmlFileNSInfoProvider.EP_NAME.getExtensionList();
      ExternalAnnotatorsFilter.EXTENSION_POINT_NAME.getExtensionList();
      IndexPatternBuilder.EP_NAME.getExtensionList();
    }
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      // return default value to avoid unnecessary save
      DaemonCodeAnalyzerSettings daemonCodeAnalyzerSettings = ApplicationManager.getApplication().getServiceIfCreated(DaemonCodeAnalyzerSettings.class);
      if (daemonCodeAnalyzerSettings != null) {
        daemonCodeAnalyzerSettings.setImportHintEnabled(true);
      }
      Project project = getProject();
      if (project != null) {
        StartupManager startupManager = project.getServiceIfCreated(StartupManager.class);
        if (startupManager != null) {
          StartupActivityTestUtil.waitForProjectActivitiesToComplete(project);

          ((StartupManagerImpl)startupManager).checkCleared();
        }
        DaemonCodeAnalyzer daemonCodeAnalyzer = project.getServiceIfCreated(DaemonCodeAnalyzer.class);
        if (daemonCodeAnalyzer != null) {
          ((DaemonCodeAnalyzerImpl)daemonCodeAnalyzer).cleanupAfterTest();
        }
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    // when running very long tasks (e.g. during stress/perf tests) CoreProgressManager might decide to de-prioritize background processes,
    // defeating the whole purpose. Do not let him do that.
    ((CoreProgressManager)ProgressManager.getInstance()).<Void, Throwable>suppressAllDeprioritizationsDuringLongTestsExecutionIn(()-> {
      super.runTestRunnable(testRunnable);
      return null;
    });
  }

  protected final void enableInspectionTool(@NotNull InspectionProfileEntry tool) {
    InspectionsKt.enableInspectionTool(getProject(), tool, getTestRootDisposable());
  }

  protected void enableInspectionTools(InspectionProfileEntry @NotNull ... tools) {
    InspectionsKt.enableInspectionTools(getProject(), getTestRootDisposable(), tools);
  }

  protected void disableInspectionTool(@NotNull String shortName){
    InspectionProfileImpl profile = InspectionProjectProfileManager.getInstance(getProject()).getCurrentProfile();
    if (profile.getInspectionTool(shortName, getProject()) != null) {
      profile.setToolEnabled(shortName, false);
    }
  }

  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return LocalInspectionTool.EMPTY_ARRAY;
  }

  protected static LocalInspectionTool[] createLocalInspectionTools(InspectionToolProvider... provider) {
    List<LocalInspectionTool> result = new ArrayList<>();
    for (InspectionToolProvider toolProvider : provider) {
      for (Class<? extends LocalInspectionTool> aClass : toolProvider.getInspectionClasses()) {
        try {
          LocalInspectionTool tool = InspectionTestUtil.instantiateTool(aClass);
          result.add(tool);
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }
    return result.toArray(LocalInspectionTool.EMPTY_ARRAY);
  }

  protected void doTest(@NonNls @NotNull String filePath, boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings) throws Exception {
    configureByFile(filePath);
    doDoTest(checkWarnings, checkInfos, checkWeakWarnings);
  }

  protected void doTest(@NonNls @NotNull String filePath, boolean checkWarnings, boolean checkInfos) throws Exception {
    doTest(filePath, checkWarnings, checkInfos, false);
  }

  protected void doTest(@NonNls @NotNull String filePath, @NonNls String projectRoot, boolean checkWarnings, boolean checkInfos) throws Exception {
    configureByFile(filePath, projectRoot);
    doDoTest(checkWarnings, checkInfos);
  }

  @NotNull
  protected HighlightTestInfo testFile(@NonNls String @NotNull ... filePath) {
    return new HighlightTestInfo(getTestRootDisposable(), filePath) {
      @Override
      public HighlightTestInfo doTest() {
        try { configureByFiles(projectRoot, filePaths); }
        catch (Exception e) { throw new RuntimeException(e); }
        ExpectedHighlightingData data = new JavaExpectedHighlightingData(myEditor.getDocument(), checkWarnings, checkWeakWarnings, checkInfos);
        if (checkSymbolNames) data.checkSymbolNames();
        checkHighlighting(data);
        return this;
      }
    };
  }

  protected void doTest(@NotNull VirtualFile vFile, boolean checkWarnings, boolean checkInfos) throws Exception {
    doTest(new VirtualFile[] { vFile }, checkWarnings, checkInfos );
  }

  protected void doTest(VirtualFile @NotNull [] vFile, boolean checkWarnings, boolean checkInfos) throws Exception {
    configureByFiles(null, vFile);
    doDoTest(checkWarnings, checkInfos);
  }

  protected void doTest(boolean checkWarnings, boolean checkInfos, String @NotNull ... files) throws Exception {
    configureByFiles(null, files);
    doDoTest(checkWarnings, checkInfos);
  }

  @NotNull
  protected Collection<HighlightInfo> doDoTest(boolean checkWarnings, boolean checkInfos) {
    return doDoTest(checkWarnings, checkInfos, false);
  }

  protected Collection<HighlightInfo> doDoTest(final boolean checkWarnings, final boolean checkInfos, final boolean checkWeakWarnings) {
    return ContainerUtil.filter(
      checkHighlighting(new ExpectedHighlightingData(myEditor.getDocument(), checkWarnings, checkWeakWarnings, checkInfos)),
      info -> info.getSeverity() == HighlightSeverity.INFORMATION && checkInfos ||
              info.getSeverity() == HighlightSeverity.WARNING && checkWarnings ||
              info.getSeverity() == HighlightSeverity.WEAK_WARNING && checkWeakWarnings ||
              info.getSeverity().compareTo(HighlightSeverity.WARNING) > 0);
  }

  @NotNull
  protected Collection<HighlightInfo> checkHighlighting(@NotNull final ExpectedHighlightingData data) {
    data.init();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    PsiManagerEx.getInstanceEx(getProject()).setAssertOnFileLoadingFilter(myVirtualFileFilter, getTestRootDisposable());

    try {
      Collection<HighlightInfo> infos = doHighlighting();

      String text = myEditor.getDocument().getText();
      doCheckResult(data, infos, text);
      return infos;
    }
    finally {
      PsiManagerEx.getInstanceEx(getProject()).setAssertOnFileLoadingFilter(VirtualFileFilter.NONE, getTestRootDisposable());
    }
  }

  protected void doCheckResult(@NotNull ExpectedHighlightingData data,
                               @NotNull Collection<? extends HighlightInfo> infos,
                               @NotNull String text) {
    PsiFile file = getFile();
    ActionUtil.underModalProgress(myProject, "", () -> {
      //line marker tooltips are called in BGT in production
      data.checkLineMarkers(file, DaemonCodeAnalyzerImpl.getLineMarkers(getDocument(file), getProject()), text);
      return null;
    });
    data.checkResult(file, infos, text);
  }

  @Override
  protected Editor createEditor(@NotNull VirtualFile file) {
    if (myVirtualFileFilter instanceof FileTreeAccessFilter) {
      allowTreeAccessForFile(file);
    }
    return super.createEditor(file);
  }

  protected void setVirtualFileFilter(@NotNull VirtualFileFilter filter) {
    myVirtualFileFilter = filter;
  }

  protected void allowTreeAccessForFile(@NotNull VirtualFile file) {
    assert myVirtualFileFilter instanceof FileTreeAccessFilter : "configured filter does not support this method";
    ((FileTreeAccessFilter)myVirtualFileFilter).allowTreeAccessForFile(file);
  }

  protected void allowTreeAccessForAllFiles() {
    assert myVirtualFileFilter instanceof FileTreeAccessFilter : "configured filter does not support this method";
    ((FileTreeAccessFilter)myVirtualFileFilter).allowTreeAccessForAllFiles();
  }

  @NotNull
  protected final List<HighlightInfo> highlightErrors() {
    return doHighlighting(HighlightSeverity.ERROR);
  }

  @NotNull
  protected final List<HighlightInfo> doHighlighting(@NotNull HighlightSeverity minSeverity) {
    return filter(doHighlighting(), minSeverity);
  }

  @NotNull
  protected final List<HighlightInfo> doHighlighting() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    IntList toIgnore = new IntArrayList();
    if (!doTestLineMarkers()) {
      toIgnore.add(Pass.LINE_MARKERS);
      toIgnore.add(Pass.SLOW_LINE_MARKERS);
    }

    if (!doExternalValidation()) {
      toIgnore.add(Pass.EXTERNAL_TOOLS);
    }
    if (forceExternalValidation()) {
      toIgnore.add(Pass.LINE_MARKERS);
      toIgnore.add(Pass.SLOW_LINE_MARKERS);
      toIgnore.add(Pass.LOCAL_INSPECTIONS);
      toIgnore.add(Pass.POPUP_HINTS);
      toIgnore.add(Pass.UPDATE_ALL);
    }

    boolean canChange = canChangeDocumentDuringHighlighting();
    List<HighlightInfo> infos = CodeInsightTestFixtureImpl.instantiateAndRun(getFile(), getEditor(), toIgnore.toIntArray(), canChange);

    if (!canChange) {
      Document document = getDocument(getFile());
      DaemonCodeAnalyzerEx daemonCodeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(myProject);
      daemonCodeAnalyzer.getFileStatusMap().assertAllDirtyScopesAreNull(document);
    }

    return infos;
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD, ElementType.TYPE})
  public @interface CanChangeDocumentDuringHighlighting {}

  private boolean canChangeDocumentDuringHighlighting() {
    return annotatedWith(CanChangeDocumentDuringHighlighting.class);
  }

  @NotNull
  public static List<HighlightInfo> filter(@NotNull List<? extends HighlightInfo> infos, @NotNull HighlightSeverity minSeverity) {
    return ContainerUtil.filter(infos, info -> info.getSeverity().compareTo(minSeverity) >= 0);
  }

  protected boolean doTestLineMarkers() {
    return false;
  }

  protected boolean doExternalValidation() {
    return true;
  }

  protected boolean forceExternalValidation() {
    return false;
  }

  protected static void findAndInvokeIntentionAction(@NotNull Collection<? extends HighlightInfo> infos,
                                                     @NotNull String intentionActionName,
                                                     @NotNull Editor editor,
                                                     @NotNull PsiFile file) {
    List<IntentionAction> actions = getIntentionActions(infos, editor, file);
    IntentionAction intentionAction = LightQuickFixTestCase.findActionWithText(actions, intentionActionName);

    if (intentionAction == null) {
      fail("Could not find action '" + intentionActionName+
           "'.\nAvailable actions: [" +StringUtil.join(ContainerUtil.map(actions, c -> c.getText()), ", ")+ "]\n" +
           "HighlightInfos: [" +StringUtil.join(infos, ", ")+"]");
    }
    CodeInsightTestFixtureImpl.invokeIntention(intentionAction, file, editor);
  }

  @Nullable
  protected static IntentionAction findIntentionAction(@NotNull Collection<? extends HighlightInfo> infos,
                                                       @NotNull String intentionActionName,
                                                       @NotNull Editor editor,
                                                       @NotNull PsiFile file) {
    List<IntentionAction> actions = getIntentionActions(infos, editor, file);
    return LightQuickFixTestCase.findActionWithText(actions, intentionActionName);
  }

  @NotNull
  protected static List<IntentionAction> getIntentionActions(@NotNull Collection<? extends HighlightInfo> infos,
                                                             @NotNull Editor editor,
                                                             @NotNull PsiFile file) {
    List<IntentionAction> actions = LightQuickFixTestCase.getAvailableActions(editor, file);

    final List<IntentionAction> quickFixActions = new ArrayList<>();
    for (HighlightInfo info : infos) {
      info.findRegisteredQuickFix((descriptor, range) -> {
        IntentionAction action = descriptor.getAction();
        if (!actions.contains(action) && action.isAvailable(file.getProject(), editor, file)) {
          quickFixActions.add(action);
        }
        return null;
      });
    }
    return ContainerUtil.concat(actions, quickFixActions);
  }

  public void checkHighlighting(Editor editor, boolean checkWarnings, boolean checkInfos) {
    setActiveEditor(editor);
    doDoTest(checkWarnings, checkInfos);
  }

  @NotNull
  public PsiClass createClass(@NotNull @Language("JAVA") String text) throws IOException {
    VirtualFile classVFile = WriteCommandAction.writeCommandAction(getProject()).compute(() -> {
      final PsiFileFactory factory = PsiFileFactory.getInstance(getProject());
      final PsiJavaFile javaFile = (PsiJavaFile)factory.createFileFromText("a.java", JavaFileType.INSTANCE, text);
      final String qname = javaFile.getClasses()[0].getQualifiedName();
      assertNotNull(qname);
      final VirtualFile[] files = ModuleRootManager.getInstance(myModule).getSourceRoots();
      File dir;
      if (files.length > 0) {
        dir = VfsUtilCore.virtualToIoFile(files[0]);
      }
      else {
        dir = createTempDirectory();
        VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(dir.getCanonicalPath().replace(File.separatorChar, '/'));
        addSourceContentToRoots(myModule, vDir);
      }

      File file = new File(dir, qname.replace('.', '/') + ".java");
      FileUtil.createIfDoesntExist(file);
      VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(file.getCanonicalPath().replace(File.separatorChar, '/'));
      assertNotNull(vFile);
      VfsUtil.saveText(vFile, text);
      return vFile;
    });

    IndexingTestUtil.waitUntilIndexesAreReady(getProject());
    PsiJavaFile psiFile = (PsiJavaFile)myPsiManager.findFile(classVFile);
    assertNotNull(psiFile);

    return psiFile.getClasses()[0];
  }
}