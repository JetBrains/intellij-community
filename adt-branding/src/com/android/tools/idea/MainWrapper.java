/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


/**
 * Entry point for Android Studio. A wrapper class that provides the ability to execute tasks that should be completed before startup IDE
 * and then startup the IDE.
 */
public class MainWrapper {

  // Keeps this variable the same as BLAZE_DIR_RAMDISK_EXPERIMENT in //tools/vendor/google3/aswb/java/com/google/devtools/intellij/blaze/plugin/base/src/com/google/idea/blaze/google3/qsync/ramdisk/RamdiskCacheOperations.java
  private static final String USE_RAMDISK = "blaze.experiment.system.dir.ramdisk";
  private static final String IDEA_SYSTEM_PATH_KEY = "idea.system.path";


  public static void main(String[] args) throws IOException {
    if (Boolean.getBoolean("idea.kotlin.plugin.use.k1")) {
      //noinspection UseOfSystemOutOrSystemErr
      System.err.println("Android Studio no longer supports Kotlin K1 mode; system property idea.kotlin.plugin.use.k1 will be ignored");
      System.clearProperty("idea.kotlin.plugin.use.k1");
    }
    if (Boolean.getBoolean(USE_RAMDISK)) {
      removeDanglingRamdiskSymlink();
    }
    com.intellij.idea.Main.main(args);
  }

  /**
   * Check existence of system directory get linked correctly before IDE started. This is ASwB only feature for internal usage to bypass
   * no-source-code-on-laptops policy. So only projects that has experiment value USE_RAMDISK set up will use this feature. It will link
   * system directory to some mounted ramdisk. But the link can become dangling e.g. laptop get restarted. Non-existing system directory
   * will lead to IDE start up failed. When the linked target does not exist, remove the symbolic. IDE will recreate the system directory
   * listed in IDEA_SYSTEM_PATH_KEY and {@code RamdiskCacheOperations} should recreate link when project get startup.
   */
  private static void removeDanglingRamdiskSymlink() throws IOException {
    String path = System.getProperty(IDEA_SYSTEM_PATH_KEY);
    if (path == null) {
      throw new IOException("Please setting vmoptions idea.system.path before setting up ramdisk.");
    }
    Path systemPathConfigured = Path.of(path);
    if (Files.isSymbolicLink(systemPathConfigured) && !Files.exists(systemPathConfigured)) {
      Files.delete(systemPathConfigured);
    }
  }
}