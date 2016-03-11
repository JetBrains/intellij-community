/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.jarFinder;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class IvyAttachSourceProvider extends AbstractAttachSourceProvider {
  private static final Logger LOG = Logger.getInstance(IvyAttachSourceProvider.class);

  @Nullable
  private static String extractUrl(PropertiesFile properties, String artifactName) {
    String prefix = "artifact:" + artifactName + "#source#jar#";

    for (IProperty property : properties.getProperties()) {
      String key = property.getUnescapedKey();
      if (key != null && key.startsWith(prefix) && key.endsWith(".location")) {
        return property.getUnescapedValue();
      }
    }

    return null;
  }

  @NotNull
  @Override
  public Collection<AttachSourcesAction> getActions(List<LibraryOrderEntry> orderEntries, final PsiFile psiFile) {
    VirtualFile jar = getJarByPsiFile(psiFile);
    if (jar == null) return Collections.emptyList();

    VirtualFile jarsDir = jar.getParent();
    if (jarsDir == null || !jarsDir.getName().equals("jars")) return Collections.emptyList();

    String jarNameWithoutExt = jar.getNameWithoutExtension();

    final VirtualFile artifactDir = jarsDir.getParent();
    if (artifactDir == null) return Collections.emptyList();

    String artifactName = artifactDir.getName();

    if (!jarNameWithoutExt.startsWith(artifactName)
        || !jarNameWithoutExt.substring(artifactName.length()).startsWith("-")) {
      return Collections.emptyList();
    }

    String version = jarNameWithoutExt.substring(artifactName.length() + 1);

    VirtualFile propertiesFile = artifactDir.findChild("ivydata-" + version + ".properties");
    if (propertiesFile == null) return Collections.emptyList();

    final Library library = getLibraryFromOrderEntriesList(orderEntries);
    if (library == null) return Collections.emptyList();

    final String sourceFileName = artifactName + '-' + version + "-sources.jar";

    final VirtualFile sources = artifactDir.findChild("sources");
    if (sources != null) {
      VirtualFile srcFile = sources.findChild(sourceFileName);
      if (srcFile != null) {
        // File already downloaded.
        VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(srcFile);
        if (jarRoot == null || ArrayUtil.contains(jarRoot, (Object[])library.getFiles(OrderRootType.SOURCES))) {
          return Collections.emptyList(); // Sources already attached.
        }

        return Collections.singleton(new AttachExistingSourceAction(jarRoot, library, "Attache sources from Ivy repository") );
      }
    }

    PsiFile propertiesFileFile = psiFile.getManager().findFile(propertiesFile);
    if (!(propertiesFileFile instanceof PropertiesFile)) return Collections.emptyList();

    final String url = extractUrl((PropertiesFile)propertiesFileFile, artifactName);
    if (StringUtil.isEmptyOrSpaces(url)) return Collections.emptyList();

    return Collections.singleton(new DownloadSourcesAction(psiFile.getProject(), "Downloading Ivy Sources", url) {
      @Override
      protected void storeFile(byte[] content) {
        try {
          VirtualFile existingSourcesFolder = sources;
          if (existingSourcesFolder == null) {
            existingSourcesFolder = artifactDir.createChildDirectory(this, "sources");
          }

          VirtualFile srcFile = existingSourcesFolder.createChildData(this, sourceFileName);
          srcFile.setBinaryContent(content);

          addSourceFile(JarFileSystem.getInstance().getJarRootForLocalFile(srcFile), library);
        }
        catch (IOException e) {
          new Notification(myMessageGroupId,
                           "IO Error",
                           "Failed to save " + artifactDir.getPath() + "/sources/" + sourceFileName,
                           NotificationType.ERROR)
            .notify(myProject);
          LOG.warn(e);
        }
      }
    });
  }
}