// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.libraryJar;

import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.openapi.project.DumbAware;
import com.intellij.serialization.SerializationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.net.URL;
import java.util.Set;

/**
 * @author Ivan Chirkov
 */
public final class LibraryJarStatisticsService implements DumbAware {

  private static final LibraryJarStatisticsService ourInstance = new LibraryJarStatisticsService();
  private LibraryJarDescriptor[] ourDescriptors;

  public static @NotNull LibraryJarStatisticsService getInstance() {
    return ourInstance;
  }

  public @Unmodifiable Set<String> getLibraryNames() {
    return ContainerUtil.map2Set(getTechnologyDescriptors(), descriptor -> descriptor.myName);
  }

  public LibraryJarDescriptor @NotNull [] getTechnologyDescriptors() {
    if (ourDescriptors == null) {
      if (!StatisticsUploadAssistant.isCollectAllowedOrForced()) return LibraryJarDescriptor.EMPTY;
      final URL url = createVersionsUrl();
      if (url == null) return LibraryJarDescriptor.EMPTY;
      final LibraryJarDescriptors descriptors = deserialize(url);
      ourDescriptors = descriptors == null ? LibraryJarDescriptor.EMPTY : descriptors.getDescriptors();
    }
    return ourDescriptors;
  }

  private static @Nullable LibraryJarDescriptors deserialize(@Nullable URL url) {
    if (url == null) return null;

    LibraryJarDescriptors libraryJarDescriptors = null;
    try {
      libraryJarDescriptors = XmlSerializer.deserialize(url, LibraryJarDescriptors.class);
    }
    catch (SerializationException ignored) {
      //
    }
    return libraryJarDescriptors;
  }

  private static @Nullable URL createVersionsUrl() {
    return LibraryJarDescriptor.class.getResource("/com/intellij/internal/statistic/libraryJar/library-jar-statistics.xml");
  }
}
