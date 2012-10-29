package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.jps.builders.BuildTarget;

import java.io.*;

/**
 * @author nik
 */
public class BuildTargetConfiguration {
  private static final Logger LOG = Logger.getInstance(BuildTargetConfiguration.class);
  private final BuildTarget<?> myTarget;
  private final BuildTargetsState myTargetsState;
  private String myConfiguration;

  public BuildTargetConfiguration(BuildTarget<?> target, BuildTargetsState targetsState) {
    myTarget = target;
    myTargetsState = targetsState;
    myConfiguration = load();
  }

  private String load() {
    File configFile = getConfigFile();
    if (configFile.exists()) {
      try {
        return new String(FileUtil.loadFileText(configFile));
      }
      catch (IOException e) {
        LOG.info("Cannot load configuration of " + myTarget);
      }
    }
    return "";
  }

  public boolean isTargetDirty() {
    String newConfiguration = saveToString();
    if (!newConfiguration.equals(myConfiguration)) {
      LOG.debug(myTarget + " configuration was changed:");
      LOG.debug("Old:");
      LOG.debug(myConfiguration);
      LOG.debug("New:");
      LOG.debug(newConfiguration);
      LOG.debug(myTarget + " will be recompiled");
      return true;
    }
    return false;
  }

  private String saveToString() {
    StringWriter out = new StringWriter();
    //noinspection IOResourceOpenedButNotSafelyClosed
    myTarget.writeConfiguration(new PrintWriter(out), myTargetsState.getDataPaths(), myTargetsState.getBuildRootIndex());
    return out.toString();
  }

  public void save() {
    try {
      File configFile = getConfigFile();
      FileUtil.createParentDirs(configFile);
      Writer out = new BufferedWriter(new FileWriter(configFile));
      try {
        String s = saveToString();
        out.write(s);
        myConfiguration = s;
      }
      finally {
        out.close();
      }
    }
    catch (IOException e) {
      LOG.info("Cannot save configuration of " + myConfiguration, e);
    }
  }

  private File getConfigFile() {
    return new File(myTargetsState.getDataPaths().getTargetDataRoot(myTarget), "config.dat");
  }
}
