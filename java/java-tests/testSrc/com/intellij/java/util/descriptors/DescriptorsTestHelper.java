// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.util.descriptors;

import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.descriptors.ConfigFile;
import com.intellij.util.descriptors.ConfigFileContainer;
import com.intellij.util.descriptors.ConfigFileFactory;
import com.intellij.util.descriptors.ConfigFileInfo;
import com.intellij.util.descriptors.ConfigFileInfoSet;
import com.intellij.util.descriptors.ConfigFileMetaData;
import com.intellij.util.descriptors.ConfigFileMetaDataProvider;
import com.intellij.util.descriptors.ConfigFileVersion;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * The config file descriptors of a test: one meta data and its provider, with the project and the main module of a heavy fixture.
 */
final class DescriptorsTestHelper {
  private final HeavyJavaCodeInsightFixture myFixture;
  final ConfigFileMetaDataProvider myMetaDataProvider;
  final ConfigFileMetaData myMetaData;

  DescriptorsTestHelper(@NotNull HeavyJavaCodeInsightFixture fixture) {
    myFixture = fixture;
    myMetaData = new ConfigFileMetaData("", "id", "file.xml", "dir", new ConfigFileVersion[]{new ConfigFileVersion("", "")}, null, false, true,
                                        true);
    myMetaDataProvider = createMetaDataProvider(myMetaData);
  }

  private static ConfigFileMetaDataProvider createMetaDataProvider(final ConfigFileMetaData... metaData) {
    return ConfigFileFactory.getInstance().createMetaDataProvider(metaData);
  }

  ConfigFileContainer createContainer() {
    return createContainer(createConfiguration());
  }

  ConfigFileContainer createContainer(final ConfigFileInfoSet configuration) {
    final ConfigFileContainer container =
      ConfigFileFactory.getInstance().createConfigFileContainer(myFixture.getProject(), myMetaDataProvider, configuration);
    Disposer.register(myFixture.getTestRootDisposable(), container);
    return container;
  }

  ConfigFileInfoSet createConfiguration() {
    return createConfiguration(myMetaDataProvider);
  }

  ConfigFileInfoSet createConfiguration(final ConfigFileMetaDataProvider metaDataProvider) {
    return ConfigFileFactory.getInstance().createConfigFileInfoSet(metaDataProvider);
  }

  ConfigFileInfo createDescriptor() throws IOException {
    return new ConfigFileInfo(myMetaData, getOrCreateModuleDir().getUrl() + "/" + myMetaData.getFileName());
  }

  private VirtualFile getOrCreateModuleDir() throws IOException {
    Path moduleDir = myFixture.getModule().getModuleNioFile().getParent();
    Files.createDirectories(moduleDir);
    return Objects.requireNonNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(moduleDir));
  }

  static VirtualFile createDescriptorFile(@NotNull ConfigFile descriptor) {
    final String url = descriptor.getUrl();
    final String directoryUrl = url.substring(0, url.lastIndexOf('/'));
    final VirtualFile moduleDir = VirtualFileManager.getInstance().findFileByUrl(directoryUrl);
    return HeavyJavaCodeInsightFixture.createChildData(moduleDir, url.substring(url.lastIndexOf('/') + 1));
  }
}
