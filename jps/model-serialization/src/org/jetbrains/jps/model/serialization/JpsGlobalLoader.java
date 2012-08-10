package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.diagnostic.Logger;
import org.jdom.Element;
import org.jetbrains.jps.model.JpsGlobal;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * @author nik
 */
public class JpsGlobalLoader extends JpsLoaderBase {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.model.serialization.JpsGlobalLoader");
  public static final String SDK_TABLE_COMPONENT_NAME = "ProjectJdkTable";
  private final JpsGlobal myGlobal;

  public JpsGlobalLoader(JpsGlobal global, Map<String, String> pathVariables) {
    super(new JpsMacroExpander(pathVariables));
    myGlobal = global;
  }

  public static void loadGlobalSettings(JpsGlobal global, Map<String, String> pathVariables, String optionsPath) throws IOException {
    File optionsDir = new File(optionsPath).getCanonicalFile();
    new JpsGlobalLoader(global, pathVariables).load(optionsDir);
  }

  private void load(File optionsDir) {
    loadGlobalLibraries(optionsDir);
    loadSdks(optionsDir);
  }

  private void loadSdks(File optionsDir) {
    final Element root = loadRootElement(new File(optionsDir, "jdk.table.xml"));
    JpsSdkTableSerializer.loadSdks(JDomSerializationUtil.findComponent(root, SDK_TABLE_COMPONENT_NAME), myGlobal.getLibraryCollection());
  }

  private void loadGlobalLibraries(File optionsDir) {
    File file = new File(optionsDir, "applicationLibraries.xml");
    if (!file.exists()) {
      LOG.debug("Cannot load global libraries: " + file.getAbsolutePath() + " doesn't exist");
      return;
    }
    final Element root = loadRootElement(file);
    JpsLibraryTableSerializer.loadLibraries(JDomSerializationUtil.findComponent(root, "libraryTable"), myGlobal.getLibraryCollection());
  }
}
