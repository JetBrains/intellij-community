// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.util.descriptors;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.testFramework.HeavyPlatformTestCase;
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

public abstract class DescriptorsTestCase extends HeavyPlatformTestCase {
  protected ConfigFileMetaDataProvider myMetaDataProvider;
  protected ConfigFileMetaData myMetaData;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myMetaData = new ConfigFileMetaData("", "id", "file.xml", "dir", new ConfigFileVersion[]{new ConfigFileVersion("", "")}, null, false, true,
                                        true);
    myMetaDataProvider = createMetaDataProvider(myMetaData);
  }

  @Override
  protected void tearDown() throws Exception {
    myMetaData = null;
    myMetaDataProvider = null;
    super.tearDown();
  }

  private static ConfigFileMetaDataProvider createMetaDataProvider(final ConfigFileMetaData... metaData) {
    return ConfigFileFactory.getInstance().createMetaDataProvider(metaData);
  }

  protected ConfigFileContainer createContainer() {
    return createContainer(createConfiguration());
  }

  protected ConfigFileContainer createContainer(final ConfigFileInfoSet configuration) {
    final ConfigFileContainer container = ConfigFileFactory.getInstance().createConfigFileContainer(myProject, myMetaDataProvider, configuration);
    Disposer.register(getTestRootDisposable(), container);
    return container;
  }

  protected ConfigFileInfoSet createConfiguration() {
    return createConfiguration(myMetaDataProvider);
  }

  protected ConfigFileInfoSet createConfiguration(final ConfigFileMetaDataProvider metaDataProvider) {
    return ConfigFileFactory.getInstance().createConfigFileInfoSet(metaDataProvider);
  }

  protected ConfigFileInfo createDescriptor() throws IOException {
    return new ConfigFileInfo(myMetaData, getOrCreateModuleDir(myModule).getUrl() + "/" + myMetaData.getFileName());
  }

  protected VirtualFile createDescriptorFile(@NotNull ConfigFile descriptor) {
    final String url = descriptor.getUrl();
    final String directoryUrl = url.substring(0, url.lastIndexOf('/'));
    final VirtualFile moduleDir = VirtualFileManager.getInstance().findFileByUrl(directoryUrl);
    return createChildData(moduleDir, url.substring(url.lastIndexOf('/') + 1));
  }
}
