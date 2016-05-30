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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
  static final Logger LOG = Logger.getInstance(ExtensionsRootType.class);

  private static final String HASH_ALGORITHM = "MD5";
  private static final String EXTENSIONS_PATH = "extensions";
  private static final String BACKUP_FILE_EXTENSION = "old";

  ExtensionsRootType() {
    super(EXTENSIONS_PATH, "Extensions");
  }

  @NotNull
  public static ExtensionsRootType getInstance() {
    return findByClass(ExtensionsRootType.class);
  }

  @NotNull
  public static Condition<VirtualFile> regularFileFilter() {
    return new Condition<VirtualFile>() {
      private final ExtensionsRootType myRootType = getInstance();
      @Override
      public boolean value(VirtualFile file) {
        return !file.isDirectory() && !myRootType.isBackupFile(file);
      }
    };
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
    File dir = findExtensionsDirectoryImpl(pluginId, path, createIfMissing);
    return dir == null ? null : VfsUtil.findFileByIoFile(dir, true);
  }

  public void extractBundledResources(@NotNull PluginId pluginId, @NotNull String path) throws IOException {
    List<URL> bundledResources = getBundledResourceUrls(pluginId, path);
    if (bundledResources.isEmpty()) return;

    File resourcesDirectory = findExtensionsDirectoryImpl(pluginId, path, true);
    if (resourcesDirectory == null) return;

    for (URL bundledResourceDirUrl : bundledResources) {
      VirtualFile bundledResourcesDir = VfsUtil.findFileByURL(bundledResourceDirUrl);
      if (bundledResourcesDir == null || !bundledResourcesDir.isDirectory()) continue;
      extractResources(bundledResourcesDir, resourcesDirectory);
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

  public boolean isBackupFile(@NotNull VirtualFile file) {
    String extension = file.getExtension();
    return !file.isDirectory() && extension != null && extension.startsWith(BACKUP_FILE_EXTENSION);
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
  private File findExtensionsDirectoryImpl(@NotNull PluginId pluginId, @NotNull String path, boolean createIfMissing) throws IOException {
    String fullPath = getPath(pluginId, path);
    File dir = new File(FileUtil.toSystemDependentName(fullPath));
    if (createIfMissing && !dir.exists()) {
      //noinspection ResultOfMethodCallIgnored
      dir.mkdirs();
    }
    return dir.exists() && dir.isDirectory()? dir : null;
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
    ClassLoader pluginClassLoader = plugin != null ? plugin.getPluginClassLoader() : null;
    Set<URL> urls = plugin == null ? null : ContainerUtil.newLinkedHashSet(ContainerUtil.toList(pluginClassLoader.getResources(resourcesPath)));
    if (urls == null) return ContainerUtil.emptyList();

    PluginId corePluginId = PluginId.findId(PluginManagerCore.CORE_PLUGIN_ID);
    IdeaPluginDescriptor corePlugin = ObjectUtils.notNull(PluginManager.getPlugin(corePluginId));
    ClassLoader coreClassLoader = corePlugin.getPluginClassLoader();
    if (coreClassLoader != pluginClassLoader && !plugin.getUseIdeaClassLoader() && !pluginId.equals(corePluginId)) {
      urls.removeAll(ContainerUtil.toList(coreClassLoader.getResources(resourcesPath)));
    }

    return ContainerUtil.newArrayList(urls);
  }

  private static void extractResources(@NotNull VirtualFile from, @NotNull File to) throws IOException {
    VfsUtilCore.visitChildrenRecursively(from, new VirtualFileVisitor(VirtualFileVisitor.NO_FOLLOW_SYMLINKS) {
      @NotNull
      @Override
      public Result visitFileEx(@NotNull VirtualFile file) {
        try {
          return visitImpl(file);
        }
        catch (IOException e) {
          throw new VisitorException(e);
        }
      }

      Result visitImpl(@NotNull VirtualFile file) throws IOException {
        File child = new File(to, FileUtil.toSystemDependentName(ObjectUtils.notNull(VfsUtilCore.getRelativePath(file, from))));
        if (child.exists() && child.isDirectory() != file.isDirectory()) {
          renameToBackupCopy(child);
        }
        File dir = file.isDirectory() ? child : child.getParentFile();
        if (!dir.exists() && !dir.mkdirs()) {
          LOG.warn("Failed to create dir: " + dir.getPath());
          return SKIP_CHILDREN;
        }
        if (file.isDirectory()) return CONTINUE;
        if (file.getFileType().isBinary()) return CONTINUE;
        if (file.getLength() > FileUtilRt.LARGE_FOR_CONTENT_LOADING) return CONTINUE;

        String newText = FileUtil.loadTextAndClose(file.getInputStream());
        String oldText = child.exists() ? FileUtil.loadFile(child) : "";
        String newHash = hash(newText);
        String oldHash = hash(oldText);
        boolean upToDate = oldHash != null && newHash != null && StringUtil.equals(oldHash, newHash);
        if (upToDate) return CONTINUE;
        if (child.exists()) {
          renameToBackupCopy(child);
        }
        FileUtil.writeToFile(child, newText);
        return CONTINUE;
      }
    }, IOException.class);
  }

  @Nullable
  private static String hash(@NotNull String s) throws IOException {
    try {
      MessageDigest md5 = MessageDigest.getInstance(HASH_ALGORITHM);
      StringBuilder sb = new StringBuilder();
      byte[] digest = md5.digest(s.getBytes(CharsetToolkit.UTF8_CHARSET));
      for (byte b : digest) {
        sb.append(Integer.toHexString(b));
      }
      return sb.toString();
    }
    catch (NoSuchAlgorithmException e) {
      LOG.error("Hash algorithm " + HASH_ALGORITHM + " is not supported", e);
      return null;
    }
  }

  private static void renameToBackupCopy(@NotNull File file) throws IOException {
    File parent = file.getParentFile();
    int i = 0;
    String newName = file.getName() + "." + BACKUP_FILE_EXTENSION;
    while (new File(parent, newName).exists()) {
      newName = file.getName() + "." + BACKUP_FILE_EXTENSION + "_" + i;
      i++;
    }
    FileUtil.rename(file, newName);
  }

  private void extractBundledExtensionsIfNeeded(@NotNull PluginId pluginId) throws IOException {
    if (!ApplicationManager.getApplication().isDispatchThread()) return;

    IdeaPluginDescriptor plugin = PluginManager.getPlugin(pluginId);
    if (plugin == null || !ResourceVersions.getInstance().shouldUpdateResourcesOf(plugin)) return;

    extractBundledResources(pluginId, "");
    ResourceVersions.getInstance().resourcesUpdated(plugin);
  }
}
