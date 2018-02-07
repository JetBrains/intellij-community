// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SystemInfo;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.TimingLog;
import org.jetbrains.jps.model.JpsElement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/**
 * @author nik
 */
public abstract class JpsLoaderBase {
  private static final Logger LOG = Logger.getInstance(JpsLoaderBase.class);
  private static final int MAX_ATTEMPTS = 5;
  private final JpsMacroExpander myMacroExpander;

  protected JpsLoaderBase(JpsMacroExpander macroExpander) {
    myMacroExpander = macroExpander;
  }

  /**
   * Returns null if file doesn't exist
   */
  @Nullable
  protected Element loadRootElement(@NotNull Path file) {
    return loadRootElement(file, myMacroExpander);
  }

  protected <E extends JpsElement> void loadComponents(@NotNull Path dir,
                                                       @NotNull Path defaultConfigFile,
                                                       JpsElementExtensionSerializerBase<E> serializer,
                                                       final E element) {
    String fileName = serializer.getConfigFileName();
    Path configFile = fileName == null ? defaultConfigFile : dir.resolve(fileName);
    Runnable timingLog = TimingLog.startActivity("loading: " + configFile.getFileName() + ":" + serializer.getComponentName());
    Element componentTag = loadComponentData(serializer, configFile);
    if (componentTag != null) {
      serializer.loadExtension(element, componentTag);
    }
    else {
      serializer.loadExtensionWithDefaultSettings(element);
    }
    timingLog.run();
  }

  @Nullable
  protected <E extends JpsElement> Element loadComponentData(@NotNull JpsElementExtensionSerializerBase<E> serializer, @NotNull Path configFile) {
    return JDomSerializationUtil.findComponent(loadRootElement(configFile), serializer.getComponentName());
  }

  /**
   * Returns null if file doesn't exist
   */
  @Nullable
  protected static Element loadRootElement(@NotNull Path file, @NotNull JpsMacroExpander macroExpander) {
    final Element element = tryLoadRootElement(file);
    if (element != null) {
      macroExpander.substitute(element, SystemInfo.isFileSystemCaseSensitive);
    }
    return element;
  }

  @Nullable
  private static Element tryLoadRootElement(@NotNull Path file) {
    int i = 0;
    while (true) {
      try {
        return JDOMUtil.load(Files.newBufferedReader(file));
      }
      catch (NoSuchFileException e) {
        return null;
      }
      catch (IOException | JDOMException e) {
        if (++i == MAX_ATTEMPTS) {
          //noinspection InstanceofCatchParameter
          throw new CannotLoadJpsModelException(file.toFile(), "Cannot " + (e instanceof IOException ? "read" : "parse") + " file " + file.toAbsolutePath() + ": " + e.getMessage(), e);
        }

        LOG.info("Loading attempt #" + i + " failed", e);
      }

      //most likely configuration file is being written by IDE so we'll wait a little
      try {
        //noinspection BusyWait
        Thread.sleep(300);
      }
      catch (InterruptedException ignored) {
        return null;
      }
    }
  }
}
