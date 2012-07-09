package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.jps.model.JpsModelTestCase;

import java.io.File;

/**
 * @author nik
 */
public abstract class JpsSerializationTestCase extends JpsModelTestCase {
  protected static String getTestDataPath(String relativePath) {
    File baseDir = new File(PathManager.getHomePath());
    final File communityDir = new File(baseDir, "community");
    if (communityDir.exists()) {
      baseDir = communityDir;
    }
    return FileUtilRt.toSystemIndependentName(baseDir.getAbsolutePath()) + "/jps/model-serialization/testData/" + relativePath;
  }
}
