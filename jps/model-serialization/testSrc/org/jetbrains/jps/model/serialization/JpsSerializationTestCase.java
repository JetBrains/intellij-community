package org.jetbrains.jps.model.serialization;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.PathUtil;
import com.intellij.util.SystemProperties;
import org.jdom.Element;
import org.jetbrains.jps.model.JpsModelTestCase;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
      JpsProjectLoader.loadProject(myProject, getPathVariables(), path);
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
      Map<String,String> pathVariables = getPathVariables();
      JpsGlobalLoader.loadGlobalSettings(myModel.getGlobal(), pathVariables, optionsPath);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected Map<String, String> getPathVariables() {
    Map<String, String> variables = new HashMap<String, String>();
    variables.put(PathMacrosImpl.APPLICATION_HOME_MACRO_NAME, PathManagerEx.getHomePath(getClass()));
    variables.put(PathMacrosImpl.USER_HOME_MACRO_NAME, SystemProperties.getUserHome());
    return variables;
  }

  protected String getTestDataFileAbsolutePath(String relativePath) {
    return PathManagerEx.findFileUnderProjectHome(relativePath, getClass()).getAbsolutePath();
  }

  protected static Element loadModuleRootTag(File imlFile) {
    JpsMacroExpander expander = JpsProjectLoader.createModuleMacroExpander(Collections.<String, String>emptyMap(), imlFile);
    return JpsLoaderBase.loadRootElement(imlFile, expander);
  }
}
