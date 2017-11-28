/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.internal.statistic.libraryJar;

import com.intellij.facet.frameworks.SettingsConnectionService;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.xmlb.XmlSerializationException;
import com.intellij.util.xmlb.XmlSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;

/**
 * @author Ivan Chirkov
 */
public class LibraryJarStatisticsService extends SettingsConnectionService implements StartupActivity, DumbAware {
  private static final String FILE_NAME = "statistics/library-jar-statistics.xml";
  private static final String DEFAULT_SETTINGS_URL = "https://www.jetbrains.com/idea/download-assistant.xml";
  private static final String DEFAULT_SERVICE_URL = "https://frameworks.jetbrains.com";

  private static final LibraryJarStatisticsService myInstance = new LibraryJarStatisticsService();
  private LibraryJarDescriptor[] myDescriptors;

  public static LibraryJarStatisticsService getInstance() {
    return myInstance;
  }

  protected LibraryJarStatisticsService() {
    super(DEFAULT_SETTINGS_URL, DEFAULT_SERVICE_URL);
  }

  @NotNull
  public LibraryJarDescriptor[] getTechnologyDescriptors() {
    if (myDescriptors == null) {
      if (!StatisticsUploadAssistant.isSendAllowed()) return LibraryJarDescriptor.EMPTY;
      final URL url = createVersionsUrl();
      if (url == null) return LibraryJarDescriptor.EMPTY;
      final LibraryJarDescriptors descriptors = deserialize(url);
      myDescriptors = descriptors == null ? LibraryJarDescriptor.EMPTY : descriptors.getDescriptors();
    }
    return myDescriptors;
  }

  @Nullable
  private static LibraryJarDescriptors deserialize(@Nullable URL url) {
    if (url == null) return null;

    LibraryJarDescriptors libraryJarDescriptors = null;
    try {
      libraryJarDescriptors = XmlSerializer.deserialize(url, LibraryJarDescriptors.class);
    }
    catch (XmlSerializationException e) {
      //
    }
    return libraryJarDescriptors;
  }

  @Nullable
  private URL createVersionsUrl() {
    final String serviceUrl = getServiceUrl();
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

  @Override
  public void runActivity(@NotNull Project project) {
    final Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode() || application.isHeadlessEnvironment()) return;
    ApplicationManager.getApplication().executeOnPooledThread((Runnable)() -> getInstance().getTechnologyDescriptors());
  }
}
