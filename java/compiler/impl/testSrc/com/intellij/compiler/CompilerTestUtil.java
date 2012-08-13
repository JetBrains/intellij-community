package com.intellij.compiler;

import com.intellij.compiler.impl.TranslatingCompilerFilesMonitor;
import com.intellij.compiler.impl.javaCompiler.javac.JavacSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.impl.ProjectJdkTableImpl;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import org.jdom.Document;
import org.jdom.Element;
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

    JavacSettings javacSettings = JavacSettings.getInstance(project);
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
}
