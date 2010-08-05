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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Apr 11, 2002
 * Time: 5:18:36 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.testFramework;

import com.intellij.ExtensionPoints;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.GlobalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.reference.EntryPoint;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;

@SuppressWarnings({"HardCodedStringLiteral"})
public abstract class InspectionTestCase extends PsiTestCase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.testFramework.InspectionTestCase");
  private EntryPoint myUnusedCodeExtension;
  private VirtualFile ext_src;

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
    AnalysisScope scope = createAnalysisScope(sourceDir[0].getParent());

    InspectionManagerEx inspectionManager = (InspectionManagerEx) InspectionManager.getInstance(myProject);
    final GlobalInspectionContextImpl globalContext = inspectionManager.createNewGlobalContext(true);
    globalContext.setCurrentScope(scope);

    if (runDeadCodeFirst) {
      runTool(new UnusedDeclarationInspection(), scope, globalContext, inspectionManager);
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

  protected void setupRootModel(final String testDir, final VirtualFile[] sourceDir, final String sdkName) {
    VirtualFile projectDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(testDir));
    assertNotNull("could not find project dir " + testDir, projectDir);
    sourceDir[0] = projectDir.findChild("src");
    if (sourceDir[0] == null) {
      sourceDir[0] = projectDir;
    }
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(myModule);
    final ModifiableRootModel rootModel = rootManager.getModifiableModel();
    rootModel.clear();
    // configure source and output path
    final ContentEntry contentEntry = rootModel.addContentEntry(projectDir);
    contentEntry.addSourceFolder(sourceDir[0], false);
    ext_src = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(testDir + "/ext_src"));
    if (ext_src != null) {
      contentEntry.addSourceFolder(ext_src, false);
    }

    // IMPORTANT! The jdk must be obtained in a way it is obtained in the normal program!
    //ProjectJdkEx jdk = ProjectJdkTable.getInstance().getInternalJdk();

    rootModel.setSdk(getTestProjectSdk());

    rootModel.commit();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ExtensionPoint<EntryPoint> point = Extensions.getRootArea().getExtensionPoint(ExtensionPoints.DEAD_CODE_TOOL);
    myUnusedCodeExtension = new EntryPoint() {
      @NotNull
      @Override
      public String getDisplayName() {
        return "duh";
      }

      @Override
      public boolean isEntryPoint(RefElement refElement, PsiElement psiElement) {
        return isEntryPoint(psiElement);
      }

      @Override
      public boolean isEntryPoint(PsiElement psiElement) {
        return ext_src != null && VfsUtil.isAncestor(ext_src, PsiUtil.getVirtualFile(psiElement), false);
      }

      @Override
      public boolean isSelected() {
        return false;
      }

      @Override
      public void setSelected(boolean selected) {

      }

      public void readExternal(Element element) {

      }

      public void writeExternal(Element element) {

      }
    };

    point.registerExtension(myUnusedCodeExtension);
  }

  @Override
  protected void tearDown() throws Exception {
    ExtensionPoint<EntryPoint> point = Extensions.getRootArea().getExtensionPoint(ExtensionPoints.DEAD_CODE_TOOL);
    point.unregisterExtension(myUnusedCodeExtension);
    myUnusedCodeExtension = null;
    ext_src = null;
    super.tearDown();
  }

  protected Sdk getTestProjectSdk() {
    Sdk sdk = JavaSdkImpl.getMockJdk17();
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
    //sdk = JavaSdkImpl.getMockJdk("");
    return sdk;
  }

  @NonNls
  protected String getTestDataPath() {
    return PathManagerEx.getTestDataPath()+"/inspection/";
  }

}
