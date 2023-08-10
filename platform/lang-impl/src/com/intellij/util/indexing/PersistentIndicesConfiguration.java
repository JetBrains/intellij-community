// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.application.PathManager;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

final class PersistentIndicesConfiguration {
  private static final int BASE_INDICES_CONFIGURATION_VERSION = 1;

  static void saveConfiguration() {
    try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(indicesConfigurationFile())))) {
      DataInputOutputUtil.writeINT(out, BASE_INDICES_CONFIGURATION_VERSION);
      IndexVersion.savePersistentIndexStamp(out);
    }
    catch (IOException ignored) {
    }
  }

  static void loadConfiguration() {
    try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(indicesConfigurationFile())))) {
      if (DataInputOutputUtil.readINT(in) == BASE_INDICES_CONFIGURATION_VERSION) {
        IndexVersion.initPersistentIndexStamp(in);
      }
    }
    catch (IOException ignored) {
    }
  }

  private static @NotNull Path indicesConfigurationFile() {
    return PathManager.getIndexRoot().resolve("indices.config");
  }
}
