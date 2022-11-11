// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarFinder;

import com.intellij.ide.JavaUiBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

/**
 * @author Sergey Evdokimov
 */
final class IvyAttachSourceProvider extends AbstractAttachSourceProvider {

  private static final Logger LOG = Logger.getInstance(IvyAttachSourceProvider.class);

  @Override
  public @NotNull Collection<? extends AttachSourcesAction> getActions(@NotNull List<? extends LibraryOrderEntry> orderEntries,
                                                                       @NotNull PsiFile psiFile) {
    VirtualFile jar = getJarByPsiFile(psiFile);
    if (jar == null) return List.of();

    VirtualFile jarsDir = jar.getParent();
    if (jarsDir == null || !jarsDir.getName().equals("jars")) return List.of();

    VirtualFile artifactDir = jarsDir.getParent();
    if (artifactDir == null) return List.of();

    String jarNameWithoutExt = jar.getNameWithoutExtension();
    String artifactName = artifactDir.getName();

    if (!jarNameWithoutExt.startsWith(artifactName) || !jarNameWithoutExt.substring(artifactName.length()).startsWith("-")) {
      return List.of();
    }

    String version = jarNameWithoutExt.substring(artifactName.length() + 1);

    //noinspection SpellCheckingInspection
    VirtualFile propertiesFile = artifactDir.findChild("ivydata-" + version + ".properties");
    if (propertiesFile == null) return List.of();

    Library library = getLibraryFromOrderEntriesList(orderEntries);
    if (library == null) return List.of();

    String sourceFileName = artifactName + '-' + version + "-sources.jar";

    VirtualFile sources = artifactDir.findChild("sources");
    if (sources != null) {
      VirtualFile srcFile = sources.findChild(sourceFileName);
      if (srcFile != null) {
        // File already downloaded.
        VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(srcFile);
        if (jarRoot == null || ArrayUtil.contains(jarRoot, (Object[])library.getFiles(OrderRootType.SOURCES))) {
          return List.of(); // Sources already attached.
        }

        return List.of(new AttachExistingSourceAction(jarRoot, library,
                                                      JavaUiBundle.message("ivi.attach.source.provider.action.name")));
      }
    }

    String url = extractUrl(propertiesFile, artifactName);
    if (StringUtil.isEmptyOrSpaces(url)) return List.of();

    return List.of(new DownloadSourcesAction(psiFile.getProject(), "Downloading Ivy Sources", url) {
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
          String message = JavaUiBundle.message("error.message.failed.to.save.0", artifactDir.getPath() + "/sources/" + sourceFileName);
          new Notification(myMessageGroupId, JavaUiBundle.message("notification.title.io.error"), message, NotificationType.ERROR).notify(myProject);
          LOG.warn(e);
        }
      }
    });
  }

  private static @Nullable String extractUrl(VirtualFile properties, String artifactName) {
    String prefix = "artifact:" + artifactName + "#source#jar#";

    try {
      Properties p = new Properties();
      p.load(new StringReader(VfsUtilCore.loadText(properties)));
      for (Object o : p.keySet()) {
        String key = o.toString();
        if (key != null && key.startsWith(prefix) && key.endsWith(".location")) {
          return p.getProperty(key);
        }
      }
    }
    catch (Exception e) {
      LOG.debug(properties.getPath(), e);
    }

    return null;
  }
}