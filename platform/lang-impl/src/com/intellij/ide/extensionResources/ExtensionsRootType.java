// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.extensionResources;

import com.intellij.ide.plugins.*;
import com.intellij.ide.scratch.RootType;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.*;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.DigestUtil;
import kotlinx.coroutines.CompletableJob;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.JobKt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import static com.intellij.util.concurrency.AppJavaExecutorUtil.executeOnPooledIoThread;

/**
 * <p> Extensions root type provide a common interface for plugins to access resources that are modifiable by the user. </p>
 * <p>Plugin's resources are stored in a directory named %plugin-id% under extensions root.</p>
 * <p>
 * Plugins can bundle default resources. Bundled resources are searched via {@link ClassLoader#getResources(String)}
 * call to plugin's class loader passing {@link ExtensionsRootType#EXTENSIONS_PATH} concatenated with relative resource path as an argument.
 * </p>
 * <p> Bundled resources are updated automatically upon plugin version change. For bundled plugins, application version is used. </p>
 */
public final class ExtensionsRootType extends RootType {
  static final Logger LOG = Logger.getInstance(ExtensionsRootType.class);

  private static final @NonNls String EXTENSIONS_PATH = "extensions";
  private static final @NonNls String EXTERNAL_EXTENSIONS_PATH = "external-extensions";
  private static final @NonNls String BACKUP_FILE_EXTENSION = "old";

  ExtensionsRootType() {
    super(EXTENSIONS_PATH, LangBundle.message("root.type.extensions"));
  }

  public static @NotNull ExtensionsRootType getInstance() {
    return findByClass(ExtensionsRootType.class);
  }

  public static @NotNull Predicate<Path> regularFileFilter() {
    return file -> {
      try {
        if (Files.isDirectory(file) || Files.isHidden(file)) {
          return false;
        }
      }
      catch (IOException e) {
        return false;
      }

      String fileName = file.getFileName().toString();
      int index = fileName.lastIndexOf('.');
      return index >= 0 && !fileName.endsWith(".txt") && !fileName.endsWith(".properties") &&
             !fileName.regionMatches(index + 1, BACKUP_FILE_EXTENSION, 0, BACKUP_FILE_EXTENSION.length());
    };
  }

  public @Nullable PluginId getOwner(@Nullable VirtualFile resource) {
    VirtualFile file = resource == null ? null : getPluginResourcesDirectoryFor(resource);
    return file == null ? null : PluginId.findId(file.getName());
  }

  public @Nullable Path findResource(@NotNull PluginId pluginId, @NotNull String path) throws IOException {
    updateBundledResources(pluginId);
    return findExtensionImpl(pluginId, path);
  }

  public @NotNull Path findResourceDirectory(@NotNull PluginId pluginId, @NotNull String path, boolean createIfMissing) throws IOException {
    updateBundledResources(pluginId);
    return findExtensionsDirectoryImpl(pluginId, path, createIfMissing);
  }

  public void extractBundledResources(@NotNull PluginId pluginId, @NotNull String path) throws IOException {
    extractBundledResourcesImpl(pluginId, path, getBundledExtensionsResources(pluginId, path));
    extractBundledExternalResources(pluginId, path);
  }

  private void extractBundledExternalResources(@NotNull PluginId pluginId, @NotNull String path) throws IOException {
    for (ExternalResourcesUnpackExtensionBean pluginBean : ExternalResourcesUnpackExtensionBean.getPluginBeans(pluginId)) {
      PluginId dependentPluginId = PluginId.getId(pluginBean.unpackTo);
      extractBundledResourcesImpl(dependentPluginId, path, getBundledExternalResources(pluginId, dependentPluginId, path));
    }
  }

  private void extractBundledResourcesImpl(@NotNull PluginId pluginId, @NotNull String path, @NotNull List<URL> bundledResources) throws IOException {
    if (bundledResources.isEmpty()) {
      return;
    }

    Path resourcesDirectory = findExtensionsDirectoryImpl(pluginId, path, true);
    for (URL bundledResourceDirUrl : bundledResources) {
      VirtualFile bundledResourcesDir = VfsUtil.findFileByURL(bundledResourceDirUrl);
      if (bundledResourcesDir == null || !bundledResourcesDir.isDirectory()) {
        continue;
      }

      if (LOG.isTraceEnabled()) {
        LOG.trace(new Throwable("Extract bundled resources " + pluginId.getIdString() + " to " + resourcesDirectory));
      }
      extractResources(bundledResourcesDir, resourcesDirectory);
    }
  }

  @Override
  public @Nullable String substituteName(@NotNull Project project, @NotNull VirtualFile file) {
    VirtualFile resourcesDir = getPluginResourcesDirectoryFor(file);
    if (file.equals(resourcesDir)) {
      String name = getPluginResourcesRootName(resourcesDir);
      if (name != null) {
        return name;
      }
    }
    return super.substituteName(project, file);
  }

  @Nullable String getPath(@Nullable VirtualFile resource) {
    VirtualFile pluginResourcesDir = resource == null ? null : getPluginResourcesDirectoryFor(resource);
    PluginId pluginId = getOwner(pluginResourcesDir);
    return pluginResourcesDir != null && pluginId != null ? VfsUtilCore.getRelativePath(resource, pluginResourcesDir) : null;
  }

  private @Nullable Path findExtensionImpl(@NotNull PluginId pluginId, @NotNull String path) {
    Path file = Path.of(getPath(pluginId, "")).resolve(path);
    return Files.isRegularFile(file) ? file : null;
  }

  private @NotNull Path findExtensionsDirectoryImpl(@NotNull PluginId pluginId, @NotNull String path, boolean createIfMissing) throws IOException {
    Path dir = Path.of(getPath(pluginId, path));
    if (createIfMissing) {
      Files.createDirectories(dir);
    }
    return dir;
  }

  private @Nullable String getPluginResourcesRootName(VirtualFile resourcesDir) {
    PluginId ownerPluginId = getOwner(resourcesDir);
    if (ownerPluginId == null) return null;

    if (PluginManagerCore.CORE_ID.equals(ownerPluginId)) {
      return PlatformUtils.getPlatformPrefix();
    }

    IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(ownerPluginId);
    if (plugin != null) {
      return plugin.getName();
    }

    return null;
  }

  private VirtualFile getPluginResourcesDirectoryFor(@NotNull VirtualFile resource) {
    String rootPath = ScratchFileService.getInstance().getRootPath(this);
    VirtualFile root = LocalFileSystem.getInstance().findFileByPath(rootPath);
    if (root == null) {
      return null;
    }

    VirtualFile parent = resource;
    VirtualFile file = resource;
    while (parent != null && !root.equals(parent)) {
      file = parent;
      parent = file.getParent();
    }
    return parent != null && file.isDirectory() ? file : null;
  }

  private @NotNull String getPath(@NotNull PluginId pluginId, @NotNull String path) {
    return ScratchFileService.getInstance().getRootPath(this) + '/' + pluginId.getIdString() + (Strings.isEmpty(path) ? "" : '/' + path);
  }

  private static @Unmodifiable @NotNull List<URL> getBundledResourceUrls(@NotNull PluginId pluginId, @NotNull String path, @NotNull String resourceRoot) throws IOException {
    // search in enabled plugins only
    IdeaPluginDescriptorImpl plugin = (IdeaPluginDescriptorImpl)PluginManager.getInstance().findEnabledPlugin(pluginId);
    if (plugin == null) {
      return Collections.emptyList();
    }

    ClassLoader pluginClassLoader = plugin.getClassLoader();
    Enumeration<URL> resources = pluginClassLoader.getResources(resourceRoot + '/' + path);
    if (resources == null) {
      return Collections.emptyList();
    }
    else if (plugin.isUseIdeaClassLoader()) {
      return ContainerUtil.toList(resources);
    }

    Set<URL> urls = new LinkedHashSet<>();
    while (resources.hasMoreElements()) {
      urls.add(resources.nextElement());
    }
    // exclude parent classloader resources from list
    for (var it : plugin.getDependencies()) {
      IdeaPluginDescriptor descriptor = PluginManagerCore.getPlugin(it.getPluginId());
      if (descriptor == null) {
        continue;
      }
      ClassLoader loader = descriptor.getClassLoader();
      if (loader != pluginClassLoader) {
        Enumeration<URL> pluginResources = loader.getResources(resourceRoot + '/' + path);
        while (pluginResources.hasMoreElements()) {
          urls.remove(pluginResources.nextElement());
        }
      }
    }
    return new ArrayList<>(urls);
  }

  private static @Unmodifiable @NotNull List<URL> getBundledExternalResources(@NotNull PluginId plugin, @NotNull PluginId destinationPlugin, @NotNull String path) throws IOException {
    return getBundledResourceUrls(plugin, path, EXTERNAL_EXTENSIONS_PATH + '/' + destinationPlugin.getIdString());
  }

  private static @Unmodifiable @NotNull List<URL> getBundledExtensionsResources(@NotNull PluginId pluginId, @NotNull String path) throws IOException {
    return getBundledResourceUrls(pluginId, path, EXTENSIONS_PATH);
  }

  private static void extractResources(@NotNull VirtualFile from, @NotNull Path to) throws IOException {
    VfsUtilCore.visitChildrenRecursively(from, new VirtualFileVisitor<Void>(VirtualFileVisitor.NO_FOLLOW_SYMLINKS) {
      @Override
      public @NotNull Result visitFileEx(@NotNull VirtualFile file) {
        try {
          return visitImpl(file);
        }
        catch (IOException e) {
          throw new VisitorException(e);
        }
      }

      Result visitImpl(@NotNull VirtualFile file) throws IOException {
        File child = to.resolve(Objects.requireNonNull(VfsUtilCore.getRelativePath(file, from))).toFile();
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
        if (VirtualFileUtil.isTooLarge(file)) return CONTINUE;

        String newText = FileUtil.loadTextAndClose(file.getInputStream());
        String oldText = child.exists() ? FileUtil.loadFile(child) : "";
        String newHash = hash(newText);
        String oldHash = hash(oldText);
        boolean upToDate = StringUtil.equals(oldHash, newHash);
        if (upToDate) return CONTINUE;
        if (child.exists()) {
          renameToBackupCopy(child);
        }
        FileUtil.writeToFile(child, newText);
        return CONTINUE;
      }
    }, IOException.class);
  }

  private static @NotNull String hash(@NotNull String s) {
    MessageDigest md5 = DigestUtil.md5();
    StringBuilder sb = new StringBuilder();
    byte[] digest = md5.digest(s.getBytes(StandardCharsets.UTF_8));
    for (byte b : digest) {
      sb.append(Integer.toHexString(b));
    }
    return sb.toString();
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

  private final Map<IdeaPluginDescriptor, Job> updatingResources = new ConcurrentHashMap<>();

  public Job updateBundledResources(@NotNull PluginId pluginId) {
    IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(pluginId);
    if (plugin == null) {
      CompletableJob job = JobKt.Job(null);
      job.complete();
      return job;
    }

    return updatingResources.computeIfAbsent(plugin, (d) -> executeOnPooledIoThread(() -> {
      try {
        updateBundledResourcesImpl(d);
      }
      finally {
        updatingResources.remove(d);
      }
    }));
  }

  private void updateBundledResourcesImpl(@NotNull IdeaPluginDescriptor plugin) {
    for (ExternalResourcesUnpackExtensionBean pluginBean : ExternalResourcesUnpackExtensionBean.getPluginsBeUnpackedTo(plugin.getPluginId())) {
      updateBundledResourcesWithLock(pluginBean.getPluginDescriptor().getPluginId());
    }

    try {
      ResourceVersions versions = ResourceVersions.getInstance();
      if (versions.shouldUpdateResourcesOf(plugin)) {
        extractBundledResources(plugin.getPluginId(), "");
        versions.resourcesUpdated(plugin);
      }
    }
    catch (IOException e) {
      LOG.warn("Failed to extract bundled resources for plugin: " + plugin.getName(), e);
    }
  }

  private void updateBundledResourcesWithLock(PluginId pluginId) {
    IdeaPluginDescriptor ideaDesc = PluginManagerCore.getPlugin(pluginId);
    if (ideaDesc == null || updatingResources.containsKey(ideaDesc)) {
      return;
    }

    CompletableJob job = JobKt.Job(null);
    if (updatingResources.putIfAbsent(ideaDesc, job) != null) {
      job.complete();
      return;
    }

    try { // updating lock
      updateBundledResourcesImpl(ideaDesc);
    }
    finally {
      updatingResources.remove(ideaDesc);
      job.complete();
    }
  }

  @TestOnly
  public void updatePluginResources(@NotNull PluginId pluginId) {
    updateBundledResourcesWithLock(pluginId);
  }
}
