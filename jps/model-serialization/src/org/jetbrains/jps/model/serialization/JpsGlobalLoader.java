package org.jetbrains.jps.model.serialization;

import org.jdom.Element;
import org.jetbrains.jps.model.JpsGlobal;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class JpsGlobalLoader extends JpsLoaderBase {
  private final JpsGlobal myGlobal;

  public JpsGlobalLoader(JpsGlobal global, Map<String, String> pathVariables) {
    super(new JpsMacroExpander(pathVariables));
    myGlobal = global;
  }

  public static void loadGlobalSettings(JpsGlobal global, String optionsPath) throws IOException {
    File optionsDir = new File(optionsPath).getCanonicalFile();
    Map<String, String> pathVariables = new HashMap<String, String>();
    new JpsGlobalLoader(global, pathVariables).load(optionsDir);
  }

  private void load(File optionsDir) {
    loadGlobalLibraries(optionsDir);
    loadSdks(optionsDir);
  }

  private void loadSdks(File optionsDir) {
    final Element root = loadRootElement(new File(optionsDir, "jdk.table.xml"));
    JpsSdkTableSerializer.loadSdks(findComponent(root, "ProjectJdkTable"), myGlobal.getLibraryCollection());
  }

  private void loadGlobalLibraries(File optionsDir) {
    final Element root = loadRootElement(new File(optionsDir, "applicationLibraries.xml"));
    JpsLibraryTableSerializer.loadLibraries(findComponent(root, "libraryTable"), myGlobal.getLibraryCollection());
  }
}
