// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtilRt;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.serialization.impl.TimingLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
public final class JpsComponentLoader {
  private static final Logger LOG = Logger.getInstance(JpsComponentLoader.class);
  private static final int MAX_ATTEMPTS = 5;
  private final @Nullable Path myExternalConfigurationDirectory;
  private final JpsMacroExpander myMacroExpander;
  private static final Element NULL_VALUE = new Element("null");
  private final ConcurrentHashMap<Path, Element> myRootElementsCache;

  public JpsComponentLoader(@NotNull JpsMacroExpander macroExpander, @Nullable Path externalConfigurationDirectory) {
    this(macroExpander, externalConfigurationDirectory, false);
  }

  public JpsComponentLoader(@NotNull JpsMacroExpander macroExpander, @Nullable Path externalConfigurationDirectory, boolean useCache) {
    myMacroExpander = macroExpander;
    myExternalConfigurationDirectory = externalConfigurationDirectory;
    myRootElementsCache = useCache ? new ConcurrentHashMap<>() : null;
  }

  public @NotNull JpsMacroExpander getMacroExpander() {
    return myMacroExpander;
  }

  /**
   * Returns null if file doesn't exist
   */
  public @Nullable Element loadRootElement(@NotNull Path file) {
    if (myRootElementsCache == null) {
      return loadRootElement(file, myMacroExpander);
    }
    Element cached = myRootElementsCache.get(file);
    if (cached != null) {
      return cached != NULL_VALUE ? cached : null;
    }
    Element result = loadRootElement(file, myMacroExpander);
    myRootElementsCache.put(file, Objects.requireNonNullElse(result, NULL_VALUE));
    return result;
  }

  public @Nullable Element loadComponent(@NotNull Path file, @NotNull String componentName) {
    return JDomSerializationUtil.findComponent(loadRootElement(file), componentName);
  }

  public  <E extends JpsElement> void loadComponents(@NotNull Path dir,
                                                     @NotNull Path defaultConfigFile,
                                                     JpsElementExtensionSerializerBase<E> serializer,
                                                     E element) {
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

  private @Nullable <E extends JpsElement> Element loadComponentData(@NotNull JpsElementExtensionSerializerBase<E> serializer, @NotNull Path configFile) {
    String componentName = serializer.getComponentName();
    Element component = loadComponent(configFile, componentName);
    if (!(serializer instanceof JpsProjectExtensionWithExternalDataSerializer) || myExternalConfigurationDirectory == null) {
      return component;
    }
    JpsProjectExtensionWithExternalDataSerializer externalDataSerializer = (JpsProjectExtensionWithExternalDataSerializer)serializer;
    Path externalConfigFile = myExternalConfigurationDirectory.resolve(externalDataSerializer.getExternalConfigFilePath());
    if (!Files.exists(externalConfigFile)) {
      return component;
    }
    
    Element externalData = null;
    String externalComponentName = externalDataSerializer.getExternalComponentName();
    for (Element child : JDOMUtil.getChildren(loadRootElement(externalConfigFile))) {
      // be ready to handle both original name and prefixed
      if (child.getName().equals(externalComponentName) || JDomSerializationUtil.isComponent(externalComponentName, child) ||
          child.getName().equals(componentName) || JDomSerializationUtil.isComponent(componentName, child)) {
        externalData = child;
        break;
      }
    }
    if (externalData == null) {
      return component;
    }
    if (component == null) {
      return externalData;
    }
    externalDataSerializer.mergeExternalData(component, externalData);
    return component;
  }

  /**
   * Returns null if file doesn't exist
   */
  static @Nullable Element loadRootElement(@NotNull Path file, @NotNull JpsMacroExpander macroExpander) {
    final Element element = tryLoadRootElement(file);
    if (element != null) {
      macroExpander.substitute(element, SystemInfoRt.isFileSystemCaseSensitive);
    }
    return element;
  }

  public static @Nullable Element tryLoadRootElement(@NotNull Path file) {
    int i = 0;
    while (true) {
      try {
        return JDOMUtil.load(file);
      }
      catch (NoSuchFileException e) {
        return null;
      }
      catch (IOException | JDOMException e) {
        if (++i == MAX_ATTEMPTS) {
          //noinspection InstanceofCatchParameter
          throw new CannotLoadJpsModelException(file.toFile(), "Cannot " + (e instanceof IOException ? "read" : "parse") + " file " + file.toAbsolutePath() + ": " + e.getMessage(), e);
        }

        LOG.info("Loading attempt #" + i + " failed for " + file.toAbsolutePath(), e);
        try {
          LOG.info("File content: " + FileUtilRt.loadFile(file.toFile()));
        }
        catch (IOException ignored) {
        }
      }

      // the most likely configuration file is being written by IDE, so we'll wait a little
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
