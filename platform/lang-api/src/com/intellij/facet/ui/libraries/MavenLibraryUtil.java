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

package com.intellij.facet.ui.libraries;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import com.intellij.ide.IdeBundle;


@Deprecated
// see LibrariesDownloadAssistant
public class MavenLibraryUtil {
  @NonNls private static final String[] MAVEN_MIRRORS = {
    "http://repo1.maven.org/maven2/",
    "http://www.ibiblio.org/maven2/",
  };
  private static final RemoteRepositoryInfo MAVEN = new RemoteRepositoryInfo("maven", IdeBundle.message("maven.repository.presentable.name"), MAVEN_MIRRORS);

  private MavenLibraryUtil() {
  }

  @Deprecated // see LibrariesDownloadAssistant
  public static LibraryInfo createSubMavenJarInfo(@NonNls String project, @NonNls String jarName, @NonNls String version, @NonNls String... requiredClasses) {
    return createMavenJarInfo(jarName, version, project, requiredClasses);
  }

  @Deprecated // see LibrariesDownloadAssistant
  private static LibraryInfo createMavenJarInfo(final String jarName, final String version, final String downloadingPrefix, 
                                                final String... requiredClasses) {
    LibraryDownloadInfo downloadInfo = new LibraryDownloadInfo(MAVEN, downloadingPrefix + "/" + jarName + "/" + version + "/" + jarName + "-" + version + ".jar",
                                                               jarName, ".jar");
    return new LibraryInfo(jarName+ ".jar", downloadInfo, requiredClasses);
  }

  @Deprecated // see LibrariesDownloadAssistant
  public static LibraryInfo createMavenJarInfo(@NonNls String jarName, @NonNls String version, @NotNull @NonNls String... requiredClasses) {
    return createMavenJarInfo(jarName, version, jarName, requiredClasses);
  }
}
