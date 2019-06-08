// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.libraryJar;

import com.intellij.facet.frameworks.LibrariesDownloadConnectionService;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.serialization.SerializationException;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.xmlb.XmlSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;

/**
 * @author Ivan Chirkov
 */
public class LibraryJarStatisticsService implements DumbAware {

  private static final String FILE_NAME = "statistics/library-jar-statistics.xml";

  private static final LibraryJarStatisticsService ourInstance = new LibraryJarStatisticsService();
  private LibraryJarDescriptor[] ourDescriptors;

  @NotNull
  public static LibraryJarStatisticsService getInstance() {
    return ourInstance;
  }

  @NotNull
  public LibraryJarDescriptor[] getTechnologyDescriptors() {
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
    final String serviceUrl = LibrariesDownloadConnectionService.getInstance().getServiceUrl();
    if (StringUtil.isNotEmpty(serviceUrl)) {
      try {
        final String url = serviceUrl + "/" + FILE_NAME;
        HttpConfigurable.getInstance().prepareURL(url);

        return new URL(url);
      }
      catch (IOException e) {
        // no route to host, unknown host, malformed url, etc.
      }
    }

    return null;
  }
}
