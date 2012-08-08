package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.PathUtil;
import org.jdom.Element;
import org.jetbrains.jps.model.JpsModelTestCase;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

/**
 * @author nik
 */
public abstract class JpsSerializationTestCase extends JpsModelTestCase {
  private String myProjectHomePath;

  protected void loadProject(final String relativePath) {
    final String path = getTestDataFileAbsolutePath(relativePath);

    myProjectHomePath = FileUtilRt.toSystemIndependentName(path);
    if (myProjectHomePath.endsWith(".ipr")) {
      myProjectHomePath = PathUtil.getParentPath(myProjectHomePath);
    }
    try {
      JpsProjectLoader.loadProject(myModel.getProject(), Collections.<String, String>emptyMap(), path);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected String getUrl(String relativePath) {
    return VfsUtilCore.pathToUrl(myProjectHomePath + "/" + relativePath);
  }

  protected static String getTestDataFileAbsolutePath(String relativePath) {
    File baseDir = new File(PathManager.getHomePath());
    File file = new File(baseDir, FileUtilRt.toSystemDependentName(relativePath));
    if (!file.exists()) {
      final File communityDir = new File(baseDir, "community");
      if (communityDir.exists()) {
        file = new File(communityDir, FileUtilRt.toSystemDependentName(relativePath));
      }
    }
    return FileUtilRt.toSystemDependentName(file.getAbsolutePath());
  }

  protected static Element loadModuleRootTag(File imlFile) {
    return JpsLoaderBase
      .loadRootElement(imlFile, JpsProjectLoader.createModuleMacroExpander(Collections.<String, String>emptyMap(), imlFile));
  }
}
