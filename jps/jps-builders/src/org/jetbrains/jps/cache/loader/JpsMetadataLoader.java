package org.jetbrains.jps.cache.loader;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.cache.client.JpsNettyClient;
import org.jetbrains.jps.cache.client.JpsServerClient;
import org.jetbrains.jps.cache.model.BuildTargetState;
import org.jetbrains.jps.incremental.Utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;

public class JpsMetadataLoader {
  private static final Logger LOG = Logger.getInstance(JpsMetadataLoader.class);
  private static final String SOURCES_STATE_FILE_NAME = "target_sources_state.json";
  private final File mySourceStateFile;
  private final JpsServerClient myClient;
  private final Type myTokenType;
  private final Gson myGson;

  public JpsMetadataLoader(@NotNull String projectPath, @NotNull JpsServerClient client) {
    myClient = client;
    myGson = new Gson();
    mySourceStateFile = new File(Utils.getDataStorageRoot(projectPath), SOURCES_STATE_FILE_NAME);
    myTokenType = new TypeToken<Map<String, Map<String, BuildTargetState>>>() {}.getType();
  }

  @Nullable
  Map<String, Map<String, BuildTargetState>> loadMetadataForCommit(@NotNull JpsNettyClient nettyClient, @NotNull String metadataId) {
    // On server commitId is the same as metadataId this data simply located in different folders
    File tmpFolder = new File(PathManager.getTempPath());
    File metadataFile = myClient.downloadMetadataById(nettyClient, metadataId, tmpFolder);
    if (metadataFile == null) return null;

    try (BufferedReader bufferedReader = new BufferedReader(new FileReader(metadataFile))) {
      return myGson.fromJson(bufferedReader, myTokenType);
    }
    catch (IOException e) {
      LOG.warn("Couldn't parse content of file " + metadataFile.getName(), e);
    }
    finally {
      if (metadataFile.exists()) FileUtil.delete(metadataFile);
    }
    return null;
  }

  @Nullable
  Map<String, Map<String, BuildTargetState>> loadCurrentProjectMetadata() {
    if (!mySourceStateFile.exists()) return null;

    try (BufferedReader bufferedReader = new BufferedReader(new FileReader(mySourceStateFile))) {
      return myGson.fromJson(bufferedReader, myTokenType);
    }
    catch (IOException e) {
      LOG.warn("Couldn't parse current project metadata " + mySourceStateFile.getName(), e);
    }
    return null;
  }

  void dropCurrentProjectMetadata() {
    if (mySourceStateFile.exists()) FileUtil.delete(mySourceStateFile);
  }
}
