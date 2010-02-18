/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.facet.impl.ui.libraries.versions;

import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xmlb.XmlSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LibrariesConfigurationManager implements Disposable {

  private static final String STRING_DELIMITER = ",";
  private static final String RI_TEMPLATE = "$RI$";
  private static final String VERSION_TEMPLATE = "$VERSION$";

  @NotNull
  public static Map<LibraryVersionInfo, List<LibraryInfo>> getLibraries(final URL... urls) {
    final Map<LibraryVersionInfo, List<LibraryInfo>> versionLibs = new HashMap<LibraryVersionInfo, List<LibraryInfo>>();

    for (URL url : urls) {
      final LibrariesConfigurationInfo libs = XmlSerializer.deserialize(url, LibrariesConfigurationInfo.class);

      assert libs != null;
      assert libs.getLibraryConfigurationInfos() != null;

      final String defaultVersion = libs.getDefaultVersion();
      final String defaultRI = libs.getDefaultRI();
      final String defaultDownloadUrl = libs.getDefaultDownloadUrl();
      final String defaultPresentationUrl = libs.getDefaultPresentationUrl();


      for (LibraryConfigurationInfo libInfo : libs.getLibraryConfigurationInfos()) {
        String[] libInfoVersions = getSplitted(libInfo.getVersion());

        if (libInfoVersions.length == 0) {
             addVersionLibrary(null, versionLibs, defaultVersion, defaultRI, defaultDownloadUrl, defaultPresentationUrl, libInfo);
        } else {
          for (String infoVersion : libInfoVersions) {
            addVersionLibrary(infoVersion.trim(), versionLibs, defaultVersion, defaultRI, defaultDownloadUrl, defaultPresentationUrl, libInfo);
          }
        }
      }
    }
    return versionLibs;
  }

  private static void addVersionLibrary(@Nullable String infoVersion,
                                        Map<LibraryVersionInfo, List<LibraryInfo>> versionLibs,
                                        String defaultVersion,
                                        String defaultRI,
                                        String defaultDownloadUrl,
                                        String defaultPresentationUrl,
                                        LibraryConfigurationInfo libInfo) {
    final String version = choose(infoVersion, defaultVersion);

    assert !StringUtil.isEmptyOrSpaces(version);

    final String ri = choose(libInfo.getRI(), defaultRI);
    final String downloadUrl = choose(libInfo.getDownloadUrl(), defaultDownloadUrl);
    final String presentationdUrl = choose(libInfo.getPresentationdUrl(), defaultPresentationUrl);


    final LibraryVersionInfo versionInfo = new LibraryVersionInfo(version, ri);
    final LibraryInfo info = createLibraryInfo(downloadUrl, presentationdUrl, version, ri, libInfo);

    if (versionLibs.get(versionInfo) == null) versionLibs.put(versionInfo, new ArrayList<LibraryInfo>());

    versionLibs.get(versionInfo).add(info);
  }

  @Nullable
  private static String choose(@Nullable String str, @Nullable String defaultStr) {
    return StringUtil.isEmptyOrSpaces(str) ? defaultStr : str;
  }

  private static LibraryInfo createLibraryInfo(String downloadUrl,
                                               String presentationdUrl,
                                               String version,
                                               String ri,
                                               LibraryConfigurationInfo libInfo) {

    downloadUrl = downloadUrl.replace(VERSION_TEMPLATE, version);
    if (ri != null) {
      downloadUrl = downloadUrl.replace(RI_TEMPLATE, ri);
    }

    String jarName = libInfo.getJarName();
    String jarVersion = libInfo.getJarVersion();
    return new LibraryInfo(jarName, jarVersion == null ? version : jarVersion, downloadUrl + jarName, presentationdUrl, getSplitted(libInfo.getRequiredClasses()));
  }

  private static String[] getSplitted(@Nullable final String s) {
    if (StringUtil.isEmptyOrSpaces(s)) return ArrayUtil.EMPTY_STRING_ARRAY;

    return ArrayUtil.toStringArray(StringUtil.split(s, STRING_DELIMITER));
  }

  public void dispose() {

  }
}