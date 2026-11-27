// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.util.descriptors;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.descriptors.ConfigFile;
import com.intellij.util.descriptors.ConfigFileContainer;
import com.intellij.util.descriptors.ConfigFileInfo;
import com.intellij.util.descriptors.ConfigFileInfoSet;
import com.intellij.util.descriptors.ConfigFileListener;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.setFileText;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@TestApplication
public class FileDescriptorsContainerTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  private DescriptorsTestHelper myDescriptors;
  private ConfigFileContainer myContainer;

  @BeforeEach
  void setUp() {
    myDescriptors = new DescriptorsTestHelper(heavyFixture.get());
    myContainer = myDescriptors.createContainer();
  }

  @Test
  public void testAddRemoveDescriptor() throws IOException {
    final ConfigFileInfo configuration = myDescriptors.createDescriptor();
    myContainer.getConfiguration().addConfigFile(configuration);
    assertSame(configuration, assertThat(myContainer.getConfigFiles()).singleElement().actual().getInfo());

    myContainer.getConfiguration().removeConfigFile(configuration);
    assertEquals(0, myContainer.getConfigFiles().size());
  }


  @Test
  public void testReadWriteExternal() throws IOException {
    final ConfigFileInfoSet configuration = myDescriptors.createConfiguration();
    final ConfigFileInfo descriptor = myDescriptors.createDescriptor();
    configuration.addConfigFile(descriptor);
    Element root = new Element("root");
    configuration.writeExternal(root);

    myContainer.getConfiguration().readExternal(root);
    assertEquals(descriptor, assertThat(myContainer.getConfigFiles()).singleElement().actual().getInfo());
  }

  @Test
  public void testCreateContainer() throws IOException {
    final ConfigFileInfoSet configuration = myDescriptors.createConfiguration();
    final ConfigFileInfo descriptor = myDescriptors.createDescriptor();
    configuration.addConfigFile(descriptor);

    final ConfigFileContainer container = myDescriptors.createContainer(configuration);

    assertEquals(descriptor, assertThat(container.getConfigFiles()).singleElement().actual().getInfo());
  }

  @Test
  public void testListeners() throws IOException {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    final MyConfigFileListener listener = new MyConfigFileListener();
    myContainer.addListener(listener);

    myContainer.getConfiguration().addConfigFile(myDescriptors.createDescriptor());
    ConfigFile descriptor = assertThat(myContainer.getConfigFiles()).singleElement().actual();
    assertNull(descriptor.getVirtualFile());
    assertNull(ReadAction.computeBlocking(() -> descriptor.getPsiFile()));

    assertSame(descriptor, assertThat(listener.getAdded()).singleElement().actual());
    assertEquals(0, listener.getRemoved().length);

    final VirtualFile file = DescriptorsTestHelper.createDescriptorFile(descriptor);
    assertSame(descriptor, assertThat(listener.getChanged()).singleElement().actual());
    assertEquals(file, descriptor.getVirtualFile());
    assertNotNull(ReadAction.computeBlocking(() -> descriptor.getPsiFile()));

    setFileText(file, "<root />");
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(fixture.getProject()).commitAllDocuments());
    assertEquals(0, listener.getChanged().length);
    assertEquals(file, descriptor.getVirtualFile());

    VfsTestUtil.deleteFile(file);
    assertSame(descriptor, assertThat(listener.getChanged()).singleElement().actual());
    assertNull(ReadAction.computeBlocking(() -> descriptor.getPsiFile()));
    assertNull(descriptor.getVirtualFile());

    myContainer.getConfiguration().removeConfigFile(descriptor.getInfo());
    assertSame(descriptor, assertThat(listener.getRemoved()).singleElement().actual());
    assertEquals(0, listener.getAdded().length);
    assertEquals(0, listener.getChanged().length);
  }

  private static class MyConfigFileListener implements ConfigFileListener {
    private final Set<ConfigFile> myChanged = new HashSet<>();
    private final Set<ConfigFile> myAdded = new HashSet<>();
    private final Set<ConfigFile> myRemoved = new HashSet<>();

    @Override
    public void configFileAdded(@NotNull final ConfigFile configFile) {
      myAdded.add(configFile);
    }

    @Override
    public void configFileRemoved(@NotNull final ConfigFile configFile) {
      myRemoved.add(configFile);
    }

    @Override
    public void configFileChanged(@NotNull final ConfigFile descriptor) {
      myChanged.add(descriptor);
    }

    public ConfigFile[] getChanged() {
      return getConfigFiles(myChanged);
    }

    public ConfigFile[] getAdded() {
      return getConfigFiles(myAdded);
    }

    public ConfigFile[] getRemoved() {
      return getConfigFiles(myRemoved);
    }

    private static ConfigFile[] getConfigFiles(final Set<ConfigFile> changed) {
      final ConfigFile[] descriptors = changed.toArray(ConfigFile.EMPTY_ARRAY);
      changed.clear();
      return descriptors;
    }
  }
}
  
