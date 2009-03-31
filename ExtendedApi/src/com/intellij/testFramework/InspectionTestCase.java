/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Apr 11, 2002
 * Time: 5:18:36 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.testFramework;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.GlobalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.deadCode.DeadCodeInspection;
import com.intellij.codeInspection.ex.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NonNls;

import java.io.File;

@SuppressWarnings({"HardCodedStringLiteral"})
public abstract class InspectionTestCase extends PsiTestCase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.testFramework.InspectionTestCase");

  public InspectionManagerEx getManager() {
    return (InspectionManagerEx) InspectionManager.getInstance(myProject);
  }

  public void doTest(@NonNls String folderName, LocalInspectionTool tool) throws Exception {
    doTest(folderName, new LocalInspectionToolWrapper(tool));
  }
  public void doTest(@NonNls String folderName, GlobalInspectionTool tool) throws Exception {
    doTest(folderName, new GlobalInspectionToolWrapper(tool));
  }
  public void doTest(@NonNls String folderName, GlobalInspectionTool tool, boolean checkRange) throws Exception {
    doTest(folderName, new GlobalInspectionToolWrapper(tool), checkRange);
  }

  public void doTest(@NonNls String folderName, GlobalInspectionTool tool, boolean checkRange, boolean runDeadCodeFirst) throws Exception {
    doTest(folderName, new GlobalInspectionToolWrapper(tool), "java 1.4", checkRange, runDeadCodeFirst);
  }

  public void doTest(@NonNls String folderName, InspectionTool tool) throws Exception {
    doTest(folderName, tool, "java 1.4");
  }

  public void doTest(@NonNls String folderName, InspectionTool tool, final boolean checkRange) throws Exception {
    doTest(folderName, tool, "java 1.4", checkRange);
  }

  public void doTest(@NonNls String folderName, InspectionTool tool, @NonNls final String jdkName) throws Exception {
    doTest(folderName, tool, jdkName, false);
  }

  public void doTest(@NonNls String folderName, InspectionTool tool, @NonNls final String jdkName, boolean checkRange) throws Exception {
    doTest(folderName, tool, jdkName, checkRange, false);
  }

  public void doTest(@NonNls String folderName, InspectionTool tool, @NonNls final String jdkName, boolean checkRange, boolean runDeadCodeFirst) throws Exception {
    final String testDir = getTestDataPath() + "/" + folderName;
    runTool(testDir, jdkName, tool, runDeadCodeFirst);

    InspectionTestUtil.compareToolResults(tool, checkRange, testDir);
  }

  protected void runTool(@NonNls final String testDir, @NonNls final String jdkName, final InspectionTool tool) {
    runTool(testDir, jdkName, tool, false);
  }

  protected void runTool(final String testDir, final String jdkName, final InspectionTool tool, boolean runDeadCodeFirst) {
    final VirtualFile[] sourceDir = new VirtualFile[1];
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          setupRootModel(testDir, sourceDir, jdkName);
        } catch (Exception e) {
          LOG.error(e);
        }
      }
    });
    AnalysisScope scope = createAnalysisScope(sourceDir[0]);

    InspectionManagerEx inspectionManager = (InspectionManagerEx) InspectionManager.getInstance(myProject);
    final GlobalInspectionContextImpl globalContext = inspectionManager.createNewGlobalContext(true);
    globalContext.setCurrentScope(scope);

    if (runDeadCodeFirst) {
      runTool(new DeadCodeInspection(), scope, globalContext, inspectionManager);
    }
    runTool(tool, scope, globalContext, inspectionManager);
  }

  protected AnalysisScope createAnalysisScope(VirtualFile sourceDir) {
    PsiManager psiManager = PsiManager.getInstance(myProject);
    return new AnalysisScope(psiManager.findDirectory(sourceDir));
  }

  private static void runTool(final InspectionTool tool,
                              final AnalysisScope scope,
                              final GlobalInspectionContextImpl globalContext,
                              final InspectionManagerEx inspectionManager) {
    InspectionTestUtil.runTool(tool, scope, globalContext, inspectionManager);

    final GlobalJavaInspectionContextImpl javaInspectionContext =
        (GlobalJavaInspectionContextImpl)globalContext.getExtension(GlobalJavaInspectionContextImpl.CONTEXT);
    if (javaInspectionContext != null) {
      do {
        javaInspectionContext.processSearchRequests(globalContext);
      } while (tool.queryExternalUsagesRequests(inspectionManager));
    }
  }

  protected void setupRootModel(final String testDir, final VirtualFile[] sourceDir, final String jdkName) {
    VirtualFile projectDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(testDir));
    assertNotNull(projectDir);
    sourceDir[0] = projectDir.findChild("src");
    if (sourceDir[0] == null) {
      sourceDir[0] = projectDir;
    }
    VirtualFile ext_src = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(testDir + "/ext_src"));
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(myModule);
    final ModifiableRootModel rootModel = rootManager.getModifiableModel();
    rootModel.clear();
    // configure source and output path
    final ContentEntry contentEntry = rootModel.addContentEntry(projectDir);
    contentEntry.addSourceFolder(sourceDir[0], false);
    if (ext_src != null) {
      contentEntry.addSourceFolder(ext_src, false);
    }

    // IMPORTANT! The jdk must be obtained in a way it is obtained in the normal program!
    //ProjectJdkEx jdk = ProjectJdkTable.getInstance().getInternalJdk();
    Sdk jdk;
    if ("java 1.5".equals(jdkName)) {
      jdk = JavaSdkImpl.getMockJdk15(jdkName);
      LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
    }
    else {
      jdk = JavaSdkImpl.getMockJdk(jdkName);
    }

    rootModel.setSdk(jdk);

    rootModel.commit();
  }

  @NonNls
  protected String getTestDataPath() {
    return PathManagerEx.getTestDataPath()+"/inspection/";
  }

}
