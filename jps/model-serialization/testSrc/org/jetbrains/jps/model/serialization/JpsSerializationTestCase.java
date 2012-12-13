package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.application.ex.PathManagerEx;
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
    loadProjectByAbsolutePath(getTestDataFileAbsolutePath(relativePath));
  }

  protected void loadProjectByAbsolutePath(String path) {
    myProjectHomePath = FileUtilRt.toSystemIndependentName(path);
    if (myProjectHomePath.endsWith(".ipr")) {
      myProjectHomePath = PathUtil.getParentPath(myProjectHomePath);
    }
    try {
      JpsProjectLoader.loadProject(myProject, Collections.<String, String>emptyMap(), path);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected String getUrl(String relativePath) {
    return VfsUtilCore.pathToUrl(getAbsolutePath(relativePath));
  }

  protected String getAbsolutePath(String relativePath) {
    return myProjectHomePath + "/" + relativePath;
  }

  protected void loadGlobalSettings(final String optionsDir) {
    try {
      String optionsPath = getTestDataFileAbsolutePath(optionsDir);
      JpsGlobalLoader.loadGlobalSettings(myModel.getGlobal(), Collections.<String, String>emptyMap(), optionsPath);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected String getTestDataFileAbsolutePath(String relativePath) {
    return PathManagerEx.findFileUnderProjectHome(relativePath, getClass()).getAbsolutePath();
  }

  protected static Element loadModuleRootTag(File imlFile) {
    JpsMacroExpander expander = JpsProjectLoader.createModuleMacroExpander(Collections.<String, String>emptyMap(), imlFile);
    return JpsLoaderBase.loadRootElement(imlFile, expander);
  }
}
