package com.intellij.codeInsight.daemon;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ModifiableModel;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.mock.MockProgressIndicator;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.testFramework.ExpectedHighlightingData;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
import java.util.*;

public abstract class DaemonAnalyzerTestCase extends CodeInsightTestCase {
  private final Map<String, LocalInspectionTool> myAvailableTools = new THashMap<String, LocalInspectionTool>();
  private final Map<String, LocalInspectionToolWrapper> myAvailableLocalTools = new THashMap<String, LocalInspectionToolWrapper>();
  private boolean toInitializeDaemon;

  protected void setUp() throws Exception {
    super.setUp();
    final LocalInspectionTool[] tools = configureLocalInspectionTools();
    for (LocalInspectionTool tool : tools) {
      enableInspectionTool(tool);
    }

    final InspectionProfileImpl profile = new InspectionProfileImpl(PROFILE) {
      public ModifiableModel getModifiableModel() {
        mySource = this;
        return this;
      }

      public InspectionProfileEntry[] getInspectionTools() {
        final Collection<LocalInspectionToolWrapper> tools = myAvailableLocalTools.values();
        return tools.toArray(new LocalInspectionToolWrapper[tools.size()]);
      }

      public boolean isToolEnabled(HighlightDisplayKey key) {
        return key != null && myAvailableTools.containsKey(key.toString());
      }

      public HighlightDisplayLevel getErrorLevel(HighlightDisplayKey key) {
        final LocalInspectionTool localInspectionTool = key == null ? null : myAvailableTools.get(key.toString());
        return localInspectionTool != null ? localInspectionTool.getDefaultLevel() : HighlightDisplayLevel.WARNING;
      }

      public InspectionTool getInspectionTool(String shortName) {
        return myAvailableLocalTools.get(shortName);
      }
    };
    final InspectionProfileManager inspectionProfileManager = InspectionProfileManager.getInstance();
    inspectionProfileManager.addProfile(profile);
    inspectionProfileManager.setRootProfile(profile.getName());
    InspectionProjectProfileManager.getInstance(getProject()).updateProfile(profile);
    toInitializeDaemon = ((ProjectEx)getProject()).isOptimiseTestLoadSpeed();
    if (toInitializeDaemon) {
      DaemonCodeAnalyzer.getInstance(getProject()).projectOpened();
    }
  }

  protected void tearDown() throws Exception {
    if (toInitializeDaemon) {
      DaemonCodeAnalyzer.getInstance(getProject()).projectClosed();
    }
    super.tearDown();
  }

  protected void enableInspectionTool(LocalInspectionTool tool){
    final String shortName = tool.getShortName();
    final HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
    if (key == null){
      HighlightDisplayKey.register(shortName, tool.getDisplayName(), tool.getID());
    }
    myAvailableTools.put(shortName, tool);
    myAvailableLocalTools.put(shortName, new LocalInspectionToolWrapper(tool));
  }

  protected void enableInspectionToolsFromProvider(InspectionToolProvider toolProvider){
    try {
      for(Class c:toolProvider.getInspectionClasses()) {
        enableInspectionTool((LocalInspectionTool)c.newInstance());
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    } 
  }

  protected void disableInspectionTool(String shortName){
    myAvailableTools.remove(shortName);
    myAvailableLocalTools.remove(shortName);
  }

  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[0];
  }

  protected void doTest(String filePath, boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings) throws Exception {
    configureByFile(filePath);
    doDoTest(checkWarnings, checkInfos, checkWeakWarnings);
  }

  protected void doTest(String filePath, boolean checkWarnings, boolean checkInfos) throws Exception {
    doTest(filePath, checkWarnings, checkInfos, false);
  }

  protected void doTest(@NonNls String filePath, @NonNls String projectRoot, boolean checkWarnings, boolean checkInfos) throws Exception {
    configureByFile(filePath, projectRoot);
    doDoTest(checkWarnings, checkInfos);
  }

  protected void doTest(VirtualFile vFile, boolean checkWarnings, boolean checkInfos) throws Exception {
    doTest(new VirtualFile[] { vFile }, checkWarnings, checkInfos );
  }

  protected void doTest(VirtualFile[] vFile, boolean checkWarnings, boolean checkInfos) throws Exception {
    configureByFiles(null, vFile);
    doDoTest(checkWarnings, checkInfos);
  }

  protected Collection<HighlightInfo> doDoTest(boolean checkWarnings, boolean checkInfos) {
    return doDoTest(checkWarnings, checkInfos, false);
  }

  protected Collection<HighlightInfo> doDoTest(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings) {

    return checkHighlighting(new ExpectedHighlightingData(myEditor.getDocument(),checkWarnings, checkWeakWarnings, checkInfos, myFile));
  }

  protected Collection<HighlightInfo> checkHighlighting(final ExpectedHighlightingData data) {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    //to load text
    TreeUtil.clearCaches((TreeElement)myFile.getNode());

    //to initialize caches
    myPsiManager.getCacheManager().getFilesWithWord("XXX", UsageSearchContext.IN_COMMENTS, GlobalSearchScope.allScope(myProject), true);
    VirtualFileFilter javaFilesFilter = new VirtualFileFilter() {
      public boolean accept(VirtualFile file) {
        FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(file);
        return (fileType == StdFileTypes.JAVA || fileType == StdFileTypes.CLASS) && !file.getName().equals("package-info.java");
      }
    };
    final JavaPsiFacadeEx facade = getJavaFacade();
    if (facade != null) {
      facade.setAssertOnFileLoadingFilter(javaFilesFilter); // check repository work
    }

    Collection<HighlightInfo> infos = doHighlighting();

    if (facade != null) {
      facade.setAssertOnFileLoadingFilter(VirtualFileFilter.NONE);
    }

    data.checkResult(infos, myEditor.getDocument().getText());

    return infos;
  }

  protected Collection<HighlightInfo> highlightErrors() {
    Collection<HighlightInfo> infos = doHighlighting();
    Iterator<HighlightInfo> iterator = infos.iterator();
    while (iterator.hasNext()) {
      HighlightInfo info = iterator.next();
      if (info.getSeverity() != HighlightSeverity.ERROR) iterator.remove();
    }
    return infos;
  }

  protected Collection<HighlightInfo> doHighlighting() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    final List<HighlightInfo> result = new ArrayList<HighlightInfo>();

    if (doTestLineMarkers()) {
      collectLineMarkersForFile(getFile(), getEditor(), result);
    }

    result.addAll(collectHighlighInfos(getFile(), getEditor()));
    //
    boolean isToLaunchExternal = true;
    for (HighlightInfo info : result) {
      if (info.getSeverity() == HighlightSeverity.ERROR) {
        isToLaunchExternal = false;
        break;
      }
    }

    if (doTestCustomPass()) {
      TextEditorHighlightingPass pass = getCustomPass(getFile(), getEditor());
      if (pass != null) {
        pass.collectInformation(new MockProgressIndicator());
        pass.applyInformationToEditor();
        result.addAll(pass.getHighlights());
      }
    }

    if (forceExternalValidation()) {
      result.clear();
    }

    if (isToLaunchExternal && doExternalValidation() || forceExternalValidation()) {
      ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject)).getFileStatusMap().setErrorFoundFlag(getDocument(getFile()), false);
      ExternalToolPass pass = new ExternalToolPass(getFile(), getEditor(), 0, getEditor().getDocument().getTextLength());
      pass.collectInformation(new MockProgressIndicator());
      pass.applyInformationToEditor();
      result.addAll(pass.getHighlights());
    }

    return result;

    /*
    TIntArrayList toIgnore = new TIntArrayList();
    if (!doTestLineMarkers()) {
      toIgnore.add(Pass.UPDATE_OVERRIDEN_MARKERS);
      toIgnore.add(Pass.VISIBLE_LINE_MARKERS);
      toIgnore.add(Pass.LINE_MARKERS);
    }
    MockProgressIndicator progress = new MockProgressIndicator();
    List<TextEditorHighlightingPass> passes = TextEditorHighlightingPassRegistrarEx.getInstanceEx(getProject()).instantiatePasses(getFile(), getEditor(), toIgnore.toNativeArray());

    for (TextEditorHighlightingPass pass : passes) {
      pass.collectInformation(progress);
    }
    for (TextEditorHighlightingPass pass : passes) {
      pass.applyInformationToEditor();
    }

    List<HighlightInfo> infos = DaemonCodeAnalyzerImpl.getHighlights(getEditor().getDocument(), getProject());
    return infos == null ? Collections.<HighlightInfo>emptyList() : infos;
    */
  }

  protected TextEditorHighlightingPass getCustomPass(final PsiFile file, final Editor editor) {
    return null;
  }

  protected boolean doTestCustomPass() {
    return false;
  }

  private void collectLineMarkersForFile(final PsiFile file, final Editor editor, final List<HighlightInfo> result) {
    LineMarkersPass lineMarkersPass = new LineMarkersPass(myProject, file, editor.getDocument(), 0, editor.getDocument().getTextLength(), true);
    lineMarkersPass.collectInformation(new MockProgressIndicator());
    lineMarkersPass.applyInformationToEditor();
    Collection<LineMarkerInfo> infoCollection = lineMarkersPass.getMarkers();
    appendHighlightInfosFromLineMarkers(result, infoCollection);

    SlowLineMarkersPass lineMarkersPass2 = new SlowLineMarkersPass(myProject, file, editor.getDocument(), 0, editor.getDocument().getTextLength());
    lineMarkersPass2.collectInformation(new MockProgressIndicator());
    lineMarkersPass2.applyInformationToEditor();
    infoCollection = lineMarkersPass2.getMarkers();
    appendHighlightInfosFromLineMarkers(result, infoCollection);
  }

  protected boolean doTestLineMarkers() {
    return false;
  }

  private static void appendHighlightInfosFromLineMarkers(final List<HighlightInfo> result, final Collection<LineMarkerInfo> infoCollection) {
    for(LineMarkerInfo lineMarkerInfo:infoCollection) {
      GutterIconRenderer gutterIconRenderer = lineMarkerInfo.createGutterRenderer();
      final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.INFO, lineMarkerInfo.startOffset,
                                                                            lineMarkerInfo.endOffset, gutterIconRenderer != null
                                                                                                        ? gutterIconRenderer.getTooltipText()
                                                                                                        : lineMarkerInfo.getLineMarkerTooltip());
      result.add(highlightInfo);
      highlightInfo.setGutterIconRenderer(gutterIconRenderer);
    }
  }

  private static List<HighlightInfo> collectHighlighInfos(final PsiFile file, final Editor editor) {
    List<HighlightInfo> result = new ArrayList<HighlightInfo>();
    Document document = editor.getDocument();
    GeneralHighlightingPass action1 = new GeneralHighlightingPass(file.getProject(), file, document, 0, file.getTextLength(), true);
    action1.collectInformation(new MockProgressIndicator());
    action1.applyInformationToEditor();
    result.addAll(action1.getHighlights());

    PostHighlightingPassFactory phpFactory = file.getProject().getComponent(PostHighlightingPassFactory.class);
    if (phpFactory != null) {
      PostHighlightingPass php = new PostHighlightingPass(file.getProject(), file, editor, 0, file.getTextLength());
      php.collectInformation(new MockProgressIndicator());
      php.applyInformationToEditor();
      result.addAll(php.getHighlights());
    }

    LocalInspectionsPass inspectionsPass = new LocalInspectionsPass(file, document, 0, file.getTextLength());
    inspectionsPass.collectInformation(new MockProgressIndicator());
    inspectionsPass.applyInformationToEditor();
    result.addAll(inspectionsPass.getHighlights());
    return result;
  }

  protected boolean doExternalValidation() {
    return true;
  }

  protected boolean forceExternalValidation() {
    return false;
  }

  protected void findAndInvokeIntentionAction(final Collection<HighlightInfo> infos, String intentionActionName, final Editor editor,
                                              final PsiFile file) throws IncorrectOperationException {
    IntentionAction intentionAction = findIntentionAction(infos, intentionActionName, editor, file);

    assertNotNull(intentionAction);
    intentionAction.invoke(myProject, myEditor, myFile);
  }

  protected IntentionAction findIntentionAction(final Collection<HighlightInfo> infos, final String intentionActionName, final Editor editor,
                                              final PsiFile file) {
    IntentionAction intentionAction = LightQuickFixTestCase.findActionWithText(LightQuickFixTestCase.getAvailableActions(infos, editor, file),
      intentionActionName
    );

    if (intentionAction == null) {
      final List<IntentionAction> availableActions = new ArrayList<IntentionAction>();

      for (HighlightInfo info :infos) {
        if (info.quickFixActionRanges != null) {
          for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> pair : info.quickFixActionRanges) {
            IntentionAction action = pair.first.getAction();
            if (action.isAvailable(getProject(), editor, file)) availableActions.add(action);
          }
        }
      }

      intentionAction = LightQuickFixTestCase.findActionWithText(
        availableActions,
        intentionActionName
      );
    }
    return intentionAction;
  }

  public void checkHighlighting(Editor editor, boolean checkWarnings, boolean checkInfos) {
    setActiveEditor(editor);
    doDoTest(checkWarnings, checkInfos);
  }

  public PsiClass createClass(String text) throws IOException {
    return createClass(myModule, text);
  }

  protected PsiClass createClass(final Module module, final String text) throws IOException {
    final String qname =
      ((PsiJavaFile)PsiFileFactory.getInstance(getProject()).createFileFromText("a.java", text)).getClasses()[0].getQualifiedName();
    final VirtualFile[] files = ModuleRootManager.getInstance(module).getSourceRoots();
    File dir;
    if (files.length > 0) {
      dir = VfsUtil.virtualToIoFile(files[0]);
    } else {
      dir = createTempDir("unitTest");
      addSourceContentToRoots(module, LocalFileSystem.getInstance().refreshAndFindFileByPath(dir.getCanonicalPath().replace(File.separatorChar, '/')));
    }

    File file = new File(dir, qname.replace('.', '/') + ".java");
    file.getParentFile().mkdirs();
    file.createNewFile();
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(file.getCanonicalPath().replace(File.separatorChar, '/'));
    VfsUtil.saveText(vFile, text);
    return ((PsiJavaFile)myPsiManager.findFile(vFile)).getClasses()[0];
  }
}
