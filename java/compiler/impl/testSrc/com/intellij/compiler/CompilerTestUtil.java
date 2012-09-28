package com.intellij.compiler;

import com.intellij.compiler.impl.TranslatingCompilerFilesMonitor;
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration;
import com.intellij.compiler.server.BuildManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.projectRoots.impl.ProjectJdkTableImpl;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions;
import org.jetbrains.jps.model.serialization.JDomSerializationUtil;
import org.jetbrains.jps.model.serialization.JpsGlobalLoader;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * @author nik
 */
public class CompilerTestUtil {
  private CompilerTestUtil() {
  }

  public static void setupJavacForTests(Project project) {
    CompilerConfigurationImpl compilerConfiguration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(project);
    compilerConfiguration.projectOpened();
    compilerConfiguration.setDefaultCompiler(compilerConfiguration.getJavacCompiler());

    JpsJavaCompilerOptions javacSettings = JavacConfiguration.getOptions(project, JavacConfiguration.class);
    javacSettings.setTestsUseExternalCompiler(true);
  }

  public static void scanSourceRootsToRecompile(Project project) {
    // need this to emulate project opening
    final List<VirtualFile> roots = Arrays.asList(ProjectRootManager.getInstance(project).getContentSourceRoots());
    TranslatingCompilerFilesMonitor.getInstance().scanSourceContent(new TranslatingCompilerFilesMonitor.ProjectRef(project), roots, roots.size(), true);
  }

  public static void saveSdkTable() {
    try {
      ProjectJdkTableImpl table = (ProjectJdkTableImpl)ProjectJdkTable.getInstance();
      File sdkFile = table.getExportFiles()[0];
      FileUtil.createParentDirs(sdkFile);
      Element root = new Element("application");
      root.addContent(JDomSerializationUtil.createComponentElement(JpsGlobalLoader.SDK_TABLE_COMPONENT_NAME).addContent(table.getState().cloneContent()));
      JDOMUtil.writeDocument(new Document(root), sdkFile, SystemProperties.getLineSeparator());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void enableExternalCompiler(final Project project) {
    new WriteAction() {
      protected void run(final Result result) {
        CompilerWorkspaceConfiguration.getInstance(project).USE_COMPILE_SERVER = true;
        ApplicationManagerEx.getApplicationEx().doNotSave(false);
        JavaAwareProjectJdkTableImpl table = JavaAwareProjectJdkTableImpl.getInstanceEx();
        table.addJdk(table.getInternalJdk());
      }
    }.execute();
  }

  public static void disableExternalCompiler(final Project project) {
    new WriteAction() {
      protected void run(final Result result) {
        CompilerWorkspaceConfiguration.getInstance(project).USE_COMPILE_SERVER = false;
        ApplicationManagerEx.getApplicationEx().doNotSave(true);
        JavaAwareProjectJdkTableImpl table = JavaAwareProjectJdkTableImpl.getInstanceEx();
        table.removeJdk(table.getInternalJdk());
        BuildManager.getInstance().stopWatchingProject(project);
      }
    }.execute();
  }
}
