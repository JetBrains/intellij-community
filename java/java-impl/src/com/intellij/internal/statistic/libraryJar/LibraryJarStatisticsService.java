// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.libraryJar;

import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.openapi.project.DumbAware;
import com.intellij.serialization.SerializationException;
import com.intellij.util.xmlb.XmlSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;

/**
 * @author Ivan Chirkov
 */
public final class LibraryJarStatisticsService implements DumbAware {

  private static final LibraryJarStatisticsService ourInstance = new LibraryJarStatisticsService();
  private LibraryJarDescriptor[] ourDescriptors;

  @NotNull
  public static LibraryJarStatisticsService getInstance() {
    return ourInstance;
  }

  public LibraryJarDescriptor @NotNull [] getTechnologyDescriptors() {
    if (ourDescriptors == null) {
      if (!StatisticsUploadAssistant.isSendAllowed()) return LibraryJarDescriptor.EMPTY;
      final URL url = createVersionsUrl();
      if (url == null) return LibraryJarDescriptor.EMPTY;
      final LibraryJarDescriptors descriptors = deserialize(url);
      ourDescriptors = descriptors == null ? LibraryJarDescriptor.EMPTY : descriptors.getDescriptors();
    }
    return ourDescriptors;
  }

  @Nullable
  private static LibraryJarDescriptors deserialize(@Nullable URL url) {
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

  @Nullable
  private static URL createVersionsUrl() {
    return LibraryJarDescriptor.class.getResource("/com/intellij/internal/statistic/libraryJar/library-jar-statistics.xml");
  }
}
