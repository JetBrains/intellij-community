/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.descriptors;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class FileDescriptorsContainerTest extends DescriptorsTestCase {
  private ConfigFileContainer myContainer;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myContainer = createContainer();
  }

  @Override
  protected void tearDown() throws Exception {
    myContainer = null;
    super.tearDown();
  }

  public void testAddRemoveDescriptor() throws IOException {
    final ConfigFileInfo configuration = createDescriptor();
    myContainer.getConfiguration().addConfigFile(configuration);
    assertSame(configuration, assertOneElement(myContainer.getConfigFiles()).getInfo());

    myContainer.getConfiguration().removeConfigFile(configuration);
    assertEquals(0, myContainer.getConfigFiles().size());
  }


  public void testReadWriteExternal() throws IOException {
    final ConfigFileInfoSet configuration = createConfiguration();
    final ConfigFileInfo descriptor = createDescriptor();
    configuration.addConfigFile(descriptor);
    Element root = new Element("root");
    configuration.writeExternal(root);

    myContainer.getConfiguration().readExternal(root);
    assertEquals(descriptor, assertOneElement(myContainer.getConfigFiles()).getInfo());
  }

  public void testCreateContainer() throws IOException {
    final ConfigFileInfoSet configuration = createConfiguration();
    final ConfigFileInfo descriptor = createDescriptor();
    configuration.addConfigFile(descriptor);

    final ConfigFileContainer container = createContainer(configuration);

    assertEquals(descriptor, assertOneElement(container.getConfigFiles()).getInfo());
  }

  public void testListeners() throws IOException {
    final MyConfigFileListener listener = new MyConfigFileListener();
    myContainer.addListener(listener);

    myContainer.getConfiguration().addConfigFile(createDescriptor());
    ConfigFile descriptor = assertOneElement(myContainer.getConfigFiles());
    assertNull(descriptor.getVirtualFile());
    assertNull(descriptor.getPsiFile());

    assertSame(descriptor, assertOneElement(listener.getAdded()));
    assertEquals(0, listener.getRemoved().length);

    final VirtualFile file = createDescriptorFile(descriptor);
    assertSame(descriptor, assertOneElement(listener.getChanged()));
    assertEquals(file, descriptor.getVirtualFile());
    assertNotNull(descriptor.getPsiFile());

    setFileText(file, "<root />");
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    assertEquals(0, listener.getChanged().length);
    assertEquals(file, descriptor.getVirtualFile());

    delete(file);
    assertSame(descriptor, assertOneElement(listener.getChanged()));
    assertNull(descriptor.getPsiFile());
    assertNull(descriptor.getVirtualFile());

    myContainer.getConfiguration().removeConfigFile(descriptor.getInfo());
    assertSame(descriptor, assertOneElement(listener.getRemoved()));
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
  
