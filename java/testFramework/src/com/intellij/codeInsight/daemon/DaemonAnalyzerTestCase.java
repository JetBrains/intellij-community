package com.intellij.codeInsight.daemon;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeHighlighting.Pass;
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
import com.intellij.codeInspection.ex.ToolsImpl;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl;
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
import gnu.trove.THashSet;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

public abstract class DaemonAnalyzerTestCase extends CodeInsightTestCase {
  private final Map<String, LocalInspectionTool> myAvailableTools = new THashMap<String, LocalInspectionTool>();
  private final Map<String, LocalInspectionToolWrapper> myAvailableLocalTools = new THashMap<String, LocalInspectionToolWrapper>();
  private boolean toInitializeDaemon;
  protected final Set<VirtualFile> myAddedClasses = new THashSet<VirtualFile>();

  protected void setUp() throws Exception {
    super.setUp();
    ((VirtualFilePointerManagerImpl)VirtualFilePointerManagerImpl.getInstance()).cleanupForNextTest();

    final LocalInspectionTool[] tools = configureLocalInspectionTools();
    for (LocalInspectionTool tool : tools) {
      enableInspectionTool(tool);
    }

    final InspectionProfileImpl profile = new InspectionProfileImpl(PROFILE) {
      @NotNull
      public ModifiableModel getModifiableModel() {
        mySource = this;
        return this;
      }

      @NotNull
      public InspectionProfileEntry[] getInspectionTools(PsiElement element) {
        final Collection<LocalInspectionToolWrapper> tools = myAvailableLocalTools.values();
        return tools.toArray(new LocalInspectionToolWrapper[tools.size()]);
      }

      @Override
      public List<ToolsImpl> getAllEnabledInspectionTools() {
        List<ToolsImpl> result = new ArrayList<ToolsImpl>();
        for (InspectionProfileEntry entry : getInspectionTools(null)) {
          result.add(new ToolsImpl(entry, entry.getDefaultLevel(), true));
        }
        return result;
      }

      public boolean isToolEnabled(HighlightDisplayKey key, PsiElement element) {
        return key != null && myAvailableTools.containsKey(key.toString());
      }

      public HighlightDisplayLevel getErrorLevel(@NotNull HighlightDisplayKey key, PsiElement element) {
        final LocalInspectionTool localInspectionTool = myAvailableTools.get(key.toString());
        return localInspectionTool != null ? localInspectionTool.getDefaultLevel() : HighlightDisplayLevel.WARNING;
      }

      public InspectionTool getInspectionTool(@NotNull String shortName, @NotNull PsiElement element) {
        return myAvailableLocalTools.get(shortName);
      }
    };
    final InspectionProfileManager inspectionProfileManager = InspectionProfileManager.getInstance();
    inspectionProfileManager.addProfile(profile);
    inspectionProfileManager.setRootProfile(PROFILE);
    Disposer.register(getProject(), new Disposable() {
      public void dispose() {
        inspectionProfileManager.deleteProfile(PROFILE);
      }
    });
    InspectionProjectProfileManager.getInstance(getProject()).updateProfile(profile);
    InspectionProjectProfileManager.getInstance(getProject()).setProjectProfile(profile.getName());
    DaemonCodeAnalyzerImpl daemonCodeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject());
    toInitializeDaemon = !daemonCodeAnalyzer.isInitialized();
    if (toInitializeDaemon) {
      daemonCodeAnalyzer.projectOpened();
    }
    ((StartupManagerImpl)StartupManagerEx.getInstanceEx(getProject())).runStartupActivities();
    ((StartupManagerImpl)StartupManagerEx.getInstanceEx(getProject())).runPostStartupActivities();
  }

  protected void tearDown() throws Exception {
    ((StartupManagerImpl)StartupManager.getInstance(getProject())).checkCleared();
    if (toInitializeDaemon) {
      DaemonCodeAnalyzer.getInstance(getProject()).projectClosed();
    }
    super.tearDown();
    ((VirtualFilePointerManagerImpl)VirtualFilePointerManagerImpl.getInstance()).assertPointersDisposed();
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

  protected static LocalInspectionTool[] createLocalInspectionTools(final InspectionToolProvider... provider) {
    final ArrayList<LocalInspectionTool> result = new ArrayList<LocalInspectionTool>();
    for (InspectionToolProvider toolProvider : provider) {
      for (Class aClass : toolProvider.getInspectionClasses()) {
        final Object tool;
        try {
          tool = aClass.newInstance();
          assertTrue(tool instanceof LocalInspectionTool);
          result.add((LocalInspectionTool)tool);
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }
    return result.toArray(new LocalInspectionTool[result.size()]);
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
        if (myAddedClasses.contains(file)) return false;

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

    String text = myEditor.getDocument().getText();
    data.checkLineMarkers(DaemonCodeAnalyzerImpl.getLineMarkers(getDocument(getFile()), getProject()), text);
    data.checkResult(infos, text);
    return infos;
  }

  public void allowTreeAccessForFile(final VirtualFile file) {
    myAddedClasses.add(file);
  }

  protected Collection<HighlightInfo> highlightErrors() {
    return filter(doHighlighting(), HighlightSeverity.ERROR);
  }

  protected List<HighlightInfo> doHighlighting() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    TIntArrayList toIgnore = new TIntArrayList();
    if (!doTestLineMarkers()) {
      toIgnore.add(Pass.UPDATE_OVERRIDEN_MARKERS);
      toIgnore.add(Pass.VISIBLE_LINE_MARKERS);
      toIgnore.add(Pass.LINE_MARKERS);
    }
    ProgressIndicator progress = new DaemonProgressIndicator();
    List<TextEditorHighlightingPass> passes = TextEditorHighlightingPassRegistrarEx.getInstanceEx(getProject()).instantiatePasses(getFile(), getEditor(), toIgnore.toNativeArray());

    for(Iterator<TextEditorHighlightingPass> i = passes.iterator();i.hasNext();) {
      final TextEditorHighlightingPass pass = i.next();

      if ((!(pass instanceof ExternalToolPass) && forceExternalValidation()) ||
          (pass instanceof ExternalToolPass && !forceExternalValidation() && !doExternalValidation())
         ) {
        i.remove();
      }
    }

    for (TextEditorHighlightingPass pass : passes) {
      pass.collectInformation(progress);
    }
    for (TextEditorHighlightingPass pass : passes) {
      pass.applyInformationToEditor();
    }

    if (doTestLineMarkers()) {
      Document document = getDocument(getFile());
      assertTrue(((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject())).getFileStatusMap().allDirtyScopesAreNull(document));
    }

    List<HighlightInfo> infos = DaemonCodeAnalyzerImpl.getHighlights(getEditor().getDocument(), getProject());
    return infos == null ? Collections.<HighlightInfo>emptyList() : new ArrayList<HighlightInfo>(infos);
  }

  public static List<HighlightInfo> filter(final List<HighlightInfo> infos, HighlightSeverity minSeverity) {
    ArrayList<HighlightInfo> result = new ArrayList<HighlightInfo>();
    for (final HighlightInfo info : infos) {
      if (info.getSeverity().compareTo(minSeverity) >= 0) result.add(info);
    }
    return result;
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

  protected static void findAndInvokeIntentionAction(final Collection<HighlightInfo> infos, String intentionActionName, final Editor editor,
                                              final PsiFile file) throws IncorrectOperationException {
    IntentionAction intentionAction = findIntentionAction(infos, intentionActionName, editor, file);

    assertNotNull(intentionActionName, intentionAction);
    intentionAction.invoke(file.getProject(), editor, file);
  }

  protected static IntentionAction findIntentionAction(final Collection<HighlightInfo> infos, final String intentionActionName, final Editor editor,
                                              final PsiFile file) {
    List<IntentionAction> actions = LightQuickFixTestCase.getAvailableActions(editor, file);
    IntentionAction intentionAction = LightQuickFixTestCase.findActionWithText(actions, intentionActionName);

    if (intentionAction == null) {
      final List<IntentionAction> availableActions = new ArrayList<IntentionAction>();

      for (HighlightInfo info :infos) {
        if (info.quickFixActionRanges != null) {
          for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> pair : info.quickFixActionRanges) {
            IntentionAction action = pair.first.getAction();
            if (action.isAvailable(file.getProject(), editor, file)) availableActions.add(action);
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
    final String qname = ((PsiJavaFile)PsiFileFactory.getInstance(getProject()).createFileFromText("a.java", text)).getClasses()[0].getQualifiedName();
    final VirtualFile[] files = ModuleRootManager.getInstance(module).getSourceRoots();
    File dir;
    if (files.length > 0) {
      dir = VfsUtil.virtualToIoFile(files[0]);
    }
    else {
      dir = createTempDirectory();
      VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(dir.getCanonicalPath().replace(File.separatorChar, '/'));
      addSourceContentToRoots(module, vDir);
    }

    File file = new File(dir, qname.replace('.', '/') + ".java");
    FileUtil.createIfDoesntExist(file);
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(file.getCanonicalPath().replace(File.separatorChar, '/'));
    VfsUtil.saveText(vFile, text);
    return ((PsiJavaFile)myPsiManager.findFile(vFile)).getClasses()[0];
  }
}
