/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.extensionResources;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.scratch.RootType;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.ide.scratch.ScratchFileService.Option;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * <p> Extensions root type provide a common interface for plugins to access resources that are modifiable by the user. </p>
 * <p>Plugin's resources are stored in a directory named %plugin-id% under extensions root.</p>
 * <p>
 * Plugins can bundle default resources. Bundled resources are searched via {@link ClassLoader#getResources(String)}
 * call to plugin's class loader passing {@link ExtensionsRootType#EXTENSIONS_PATH} concatenated with relative resource path as an argument.
 * </p>
 * <p> Bundled resources are updated automatically upon plugin version change. For bundled plugins, application version is used. </p>
 */
public class ExtensionsRootType extends RootType {
  public static final String EXTENSIONS_PATH = "extensions";
  public static final String BACKUP_FILE_EXTENSION = "old";

  static final Logger LOG = Logger.getInstance(ExtensionsRootType.class);
  private static final String HASH_ALGORITHM = "MD5";

  ExtensionsRootType() {
    super(EXTENSIONS_PATH, "Extensions");
  }

  @NotNull
  public static ExtensionsRootType getInstance() {
    return findByClass(ExtensionsRootType.class);
  }

  @Nullable
  public PluginId getOwner(@Nullable VirtualFile resource) {
    VirtualFile file = getPluginResourcesDirectoryFor(resource);
    return file != null ? PluginId.findId(file.getName()) : null;
  }

  @Nullable
  public VirtualFile findResource(@NotNull PluginId pluginId, @NotNull String path, boolean createIfMissing) throws IOException {
    extractBundledExtensionsIfNeeded(pluginId);
    return findExtensionImpl(pluginId, path, createIfMissing);
  }

  @Nullable
  public VirtualFile findResourceDirectory(@NotNull PluginId pluginId, @NotNull String path, boolean createIfMissing) throws IOException {
    extractBundledExtensionsIfNeeded(pluginId);
    return findExtensionsDirectoryImpl(pluginId, path, createIfMissing);
  }

  public void extractBundledResources(@NotNull PluginId pluginId, @NotNull String path) throws IOException {
    List<URL> bundledResources = getBundledResourceUrls(pluginId, path);
    if (bundledResources.isEmpty()) return;

    VirtualFile resourcesDirectory = findExtensionsDirectoryImpl(pluginId, path, true);
    if (resourcesDirectory == null) return;

    Application application = ApplicationManager.getApplication();
    for (URL bundledResourceDirUrl : bundledResources) {
      VirtualFile bundledResourcesDir = VfsUtil.findFileByURL(bundledResourceDirUrl);
      if (!bundledResourcesDir.isDirectory()) continue;

      AccessToken token = application.acquireWriteActionLock(ExtensionsRootType.class);
      try {
        FileDocumentManager.getInstance().saveAllDocuments();
        extractResources(bundledResourcesDir, resourcesDirectory);
      }
      finally {
        token.finish();
      }
    }
  }

  @Nullable
  @Override
  public String substituteName(@NotNull Project project, @NotNull VirtualFile file) {
    try {
      VirtualFile resourcesDir = getPluginResourcesDirectoryFor(file);
      if (file.equals(resourcesDir)) {
        String name = getPluginResourcesRootName(resourcesDir);
        if (name != null) {
          return name;
        }
      }
    }
    catch (IOException ignore) {
    }
    return super.substituteName(project, file);
  }

  @Nullable
  String getPath(@Nullable VirtualFile resource) {
    VirtualFile pluginResourcesDir = getPluginResourcesDirectoryFor(resource);
    PluginId pluginId = getOwner(pluginResourcesDir);
    return pluginResourcesDir != null && pluginId != null ? VfsUtilCore.getRelativePath(resource, pluginResourcesDir) : null;
  }

  @Nullable
  private VirtualFile findExtensionImpl(@NotNull PluginId pluginId, @NotNull String path, boolean createIfMissing) throws IOException {
    return findFile(null, pluginId.getIdString() + "/" + path, createIfMissing ? Option.create_if_missing : Option.existing_only);
  }

  @Nullable
  private VirtualFile findExtensionsDirectoryImpl(@NotNull PluginId pluginId, @NotNull String path, boolean createIfMissing) throws IOException {
    String resourceDirPath = getPath(pluginId, path);
    LocalFileSystem fs = LocalFileSystem.getInstance();
    VirtualFile file = fs.refreshAndFindFileByPath(resourceDirPath);
    if (file == null && createIfMissing) {
      return VfsUtil.createDirectories(resourceDirPath);
    }
    return file != null && file.isDirectory() ? file : null;
  }

  @Nullable
  private String getPluginResourcesRootName(VirtualFile resourcesDir) throws IOException {
    PluginId ownerPluginId = getOwner(resourcesDir);
    if (ownerPluginId == null) return null;

    if (PluginManagerCore.CORE_PLUGIN_ID.equals(ownerPluginId.getIdString())) {
      return PlatformUtils.getPlatformPrefix();
    }

    IdeaPluginDescriptor plugin = PluginManager.getPlugin(ownerPluginId);
    if (plugin != null) {
      return plugin.getName();
    }

    return null;
  }

  @Contract("null->null")
  private VirtualFile getPluginResourcesDirectoryFor(@Nullable VirtualFile resource) {
    VirtualFile root = resource != null ? getRootDirectory() : null;
    if (root == null) return null;

    VirtualFile parent = resource;
    VirtualFile file = resource;
    while (parent != null && !root.equals(parent)) {
      file = parent;
      parent = file.getParent();
    }
    return parent != null && file.isDirectory() ? file : null;
  }

  @Nullable
  private VirtualFile getRootDirectory() {
    String path = ScratchFileService.getInstance().getRootPath(this);
    return LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
  }

  @NotNull
  private String getPath(@NotNull PluginId pluginId, @NotNull String path) {
    return ScratchFileService.getInstance().getRootPath(this) + "/" + pluginId.getIdString() + (StringUtil.isEmpty(path) ? "" : "/" + path);
  }

  @NotNull
  private static List<URL> getBundledResourceUrls(@NotNull PluginId pluginId, @NotNull String path) throws IOException {
    String resourcesPath = EXTENSIONS_PATH + "/" + path;
    IdeaPluginDescriptor plugin = PluginManager.getPlugin(pluginId);
    ClassLoader cl = plugin != null ? plugin.getPluginClassLoader() : null;
    Enumeration<URL> urlEnumeration = plugin != null ? cl.getResources(resourcesPath) : null;
    if (urlEnumeration == null) return ContainerUtil.emptyList();

    PluginId corePluginId = PluginId.findId(PluginManagerCore.CORE_PLUGIN_ID);
    Set<URL> excludedUrls = ContainerUtil.newHashSet();
    if (!plugin.getUseIdeaClassLoader() && !pluginId.equals(corePluginId)) {
      IdeaPluginDescriptor corePlugin = PluginManager.getPlugin(corePluginId);
      ClassLoader ideaClassLoader = ObjectUtils.assertNotNull(corePlugin).getPluginClassLoader();
      Enumeration<URL> resources = ideaClassLoader.getResources(resourcesPath);
      while (resources.hasMoreElements()) {
        excludedUrls.add(resources.nextElement());
      }
    }

    LinkedHashSet<URL> urls = ContainerUtil.newLinkedHashSet();
    while (urlEnumeration.hasMoreElements()) {
      URL url = urlEnumeration.nextElement();
      if (!excludedUrls.contains(url)) {
        urls.add(url);
      }
    }

    return ContainerUtil.newArrayList(urls);
  }

  private static void extractResources(@NotNull VirtualFile from, @NotNull VirtualFile to) throws IOException {
    @SuppressWarnings("UnsafeVfsRecursion") VirtualFile[] fromChildren = from.getChildren();
    for (VirtualFile fromChild : fromChildren) {
      if (fromChild.is(VFileProperty.SYMLINK) || fromChild.is(VFileProperty.SPECIAL)) continue;

      VirtualFile toChild = to.findChild(fromChild.getName());
      if (toChild != null && fromChild.isDirectory() != toChild.isDirectory()) {
        renameToBackupCopy(toChild);
        toChild = null;
      }

      if (fromChild.isDirectory()) {
        if (toChild == null) {
          toChild = to.createChildDirectory(ExtensionsRootType.class, fromChild.getName());
        }
        extractResources(fromChild, toChild);
      }
      else {
        if (toChild != null) {
          String fromHash = hash(fromChild);
          String toHash = hash(toChild);
          boolean upToDate = fromHash != null && toHash != null && StringUtil.equals(fromHash, toHash);
          if (upToDate) {
            continue;
          }
          else {
            renameToBackupCopy(toChild);
          }
        }
        toChild = to.createChildData(ExtensionsRootType.class, fromChild.getName());
        toChild.setBinaryContent(fromChild.contentsToByteArray());
      }
    }
  }

  @Nullable
  private static String hash(@NotNull VirtualFile file) throws IOException {
    try {
      MessageDigest md5 = MessageDigest.getInstance(HASH_ALGORITHM);
      StringBuilder sb = new StringBuilder();
      byte[] digest = md5.digest(file.contentsToByteArray());
      for (byte b : digest) {
        sb.append(Integer.toHexString(b));
      }
      return sb.toString();
    }
    catch (NoSuchAlgorithmException e) {
      LOG.error("Hash algorithm " + HASH_ALGORITHM + " is not supported." + e);
      return null;
    }
  }

  private static void renameToBackupCopy(@NotNull VirtualFile virtualFile) throws IOException {
    VirtualFile parent = virtualFile.getParent();
    int i = 0;
    String newName = virtualFile.getName() + "." + BACKUP_FILE_EXTENSION;
    while (parent.findChild(newName) != null) {
      newName = virtualFile.getName() + "." + BACKUP_FILE_EXTENSION + "_" + i;
      i++;
    }
    virtualFile.rename(ExtensionsRootType.class, newName);
  }

  private void extractBundledExtensionsIfNeeded(@NotNull PluginId pluginId) throws IOException {
    if (!ApplicationManager.getApplication().isDispatchThread()) return;

    IdeaPluginDescriptor plugin = PluginManager.getPlugin(pluginId);
    if (plugin == null || !ResourceVersions.getInstance().shouldUpdateResourcesOf(plugin)) return;

    extractBundledResources(pluginId, "");
    ResourceVersions.getInstance().resourcesUpdated(plugin);
  }
}
