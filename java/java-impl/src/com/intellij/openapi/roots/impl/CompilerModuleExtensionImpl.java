/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension;

import java.util.ArrayList;
import java.util.List;

public class CompilerModuleExtensionImpl extends CompilerModuleExtension {
  @NonNls private static final String OUTPUT_TAG = JpsJavaModelSerializerExtension.OUTPUT_TAG;
  @NonNls private static final String TEST_OUTPUT_TAG = JpsJavaModelSerializerExtension.TEST_OUTPUT_TAG;
  @NonNls private static final String ATTRIBUTE_URL = JpsJavaModelSerializerExtension.URL_ATTRIBUTE;
  @NonNls private static final String EXCLUDE_OUTPUT_TAG = JpsJavaModelSerializerExtension.EXCLUDE_OUTPUT_TAG;

  private String myCompilerOutput;
  private VirtualFilePointer myCompilerOutputPointer;

  private String myCompilerOutputForTests;
  private VirtualFilePointer myCompilerOutputPathForTestsPointer;

  private boolean myInheritedCompilerOutput = true;
  private boolean myExcludeOutput = true;
  @NotNull
  private final Module myModule;

  private CompilerModuleExtensionImpl mySource;
  private boolean myWritable;
  private boolean myDisposed;

  public CompilerModuleExtensionImpl(@NotNull Module module) {
    myModule = module;
  }

  private CompilerModuleExtensionImpl(@NotNull CompilerModuleExtensionImpl source, final boolean writable) {
    this(source.myModule);
    myWritable = writable;
    myCompilerOutput = source.myCompilerOutput;
    myCompilerOutputPointer = duplicatePointer(source.myCompilerOutputPointer);
    myCompilerOutputForTests = source.myCompilerOutputForTests;
    myCompilerOutputPathForTestsPointer = duplicatePointer(source.myCompilerOutputPathForTestsPointer);
    myInheritedCompilerOutput = source.myInheritedCompilerOutput;
    myExcludeOutput = source.myExcludeOutput;
    mySource = source;
  }

  private VirtualFilePointer duplicatePointer(VirtualFilePointer pointer) {
    if (pointer == null) return null;
    final VirtualFilePointerManager filePointerManager = VirtualFilePointerManager.getInstance();
    return filePointerManager.duplicate(pointer, this, null);
  }

  @Override
  public void readExternal(@NotNull Element element) {
    assert !myDisposed;
    myInheritedCompilerOutput = Boolean.parseBoolean(element.getAttributeValue(JpsJavaModelSerializerExtension.INHERIT_COMPILER_OUTPUT_ATTRIBUTE, "false"));
    myExcludeOutput = element.getChild(EXCLUDE_OUTPUT_TAG) != null;

    myCompilerOutputPointer = getOutputPathValue(element, OUTPUT_TAG, !myInheritedCompilerOutput);

    myCompilerOutput = getOutputPathValue(element, OUTPUT_TAG);

    myCompilerOutputPathForTestsPointer = getOutputPathValue(element, TEST_OUTPUT_TAG, !myInheritedCompilerOutput);

    myCompilerOutputForTests = getOutputPathValue(element, TEST_OUTPUT_TAG);
  }

  @Override
  public void writeExternal(@NotNull Element element) {
    assert !myDisposed;
    if (!myInheritedCompilerOutput) {
      if (myCompilerOutput != null) {
        final Element pathElement = new Element(OUTPUT_TAG);
        pathElement.setAttribute(ATTRIBUTE_URL, myCompilerOutput);
        element.addContent(pathElement);
      }
      if (myCompilerOutputForTests != null) {
        final Element pathElement = new Element(TEST_OUTPUT_TAG);
        pathElement.setAttribute(ATTRIBUTE_URL, myCompilerOutputForTests);
        element.addContent(pathElement);
      }
    }
    else {
      element.setAttribute(JpsJavaModelSerializerExtension.INHERIT_COMPILER_OUTPUT_ATTRIBUTE, "true");
    }

    if (myExcludeOutput) {
      element.addContent(new Element(EXCLUDE_OUTPUT_TAG));
    }
  }

  @Nullable
  private VirtualFilePointer getOutputPathValue(Element element, String tag, final boolean createPointer) {
    final Element outputPathChild = element.getChild(tag);
    VirtualFilePointer vptr = null;
    if (outputPathChild != null && createPointer) {
      String outputPath = outputPathChild.getAttributeValue(ATTRIBUTE_URL);
      vptr = createPointer(outputPath);
    }
    return vptr;
  }

  @Nullable
  private static String getOutputPathValue(@NotNull Element element, @NotNull String tag) {
    final Element outputPathChild = element.getChild(tag);
    if (outputPathChild != null) {
      return outputPathChild.getAttributeValue(ATTRIBUTE_URL);
    }
    return null;
  }

  @Override
  @Nullable
  public VirtualFile getCompilerOutputPath() {
    if (myInheritedCompilerOutput) {
      final VirtualFile projectOutputPath = CompilerProjectExtension.getInstance(getProject()).getCompilerOutput();
      if (projectOutputPath == null) return null;
      return projectOutputPath.findFileByRelativePath(PRODUCTION + "/" + getSanitizedModuleName());
    }
    return myCompilerOutputPointer == null ? null : myCompilerOutputPointer.getFile();
  }

  @Override
  @Nullable
  public VirtualFile getCompilerOutputPathForTests() {
    if (myInheritedCompilerOutput) {
      final VirtualFile projectOutputPath = CompilerProjectExtension.getInstance(getProject()).getCompilerOutput();
      if (projectOutputPath == null) return null;
      return projectOutputPath.findFileByRelativePath(TEST + "/" + getSanitizedModuleName());
    }
    return myCompilerOutputPathForTestsPointer == null ? null : myCompilerOutputPathForTestsPointer.getFile();
  }

  @Override
  @Nullable
  public String getCompilerOutputUrl() {
    if (myInheritedCompilerOutput) {
      final String projectOutputPath = CompilerProjectExtension.getInstance(getProject()).getCompilerOutputUrl();
      if (projectOutputPath == null) return null;
      return projectOutputPath + "/" + PRODUCTION + "/" + getSanitizedModuleName();
    }
    return myCompilerOutputPointer == null ? null : myCompilerOutputPointer.getUrl();
  }

  @Override
  @Nullable
  public String getCompilerOutputUrlForTests() {
    if (myInheritedCompilerOutput) {
      final String projectOutputPath = CompilerProjectExtension.getInstance(getProject()).getCompilerOutputUrl();
      if (projectOutputPath == null) return null;
      return projectOutputPath + "/" + TEST + "/" + getSanitizedModuleName();
    }
    return myCompilerOutputPathForTestsPointer == null ? null : myCompilerOutputPathForTestsPointer.getUrl();
  }

  @NotNull
  private String getSanitizedModuleName() {
    Module module = getModule();
    VirtualFile file = module.getModuleFile();
    return file != null ? file.getNameWithoutExtension() : module.getName();
  }

  @Override
  public void setCompilerOutputPath(final VirtualFile file) {
    setCompilerOutputPath(file == null ? null : file.getUrl());
  }

  @NotNull
  private VirtualFilePointer createPointer(@NotNull String url) {
    return VirtualFilePointerManager.getInstance().create(url, this, ProjectRootManagerImpl.getInstanceImpl(getProject()).getRootsValidityChangedListener());
  }

  @Override
  public void setCompilerOutputPath(final String url) {
    assertWritable();
    myCompilerOutput = url;
    myCompilerOutputPointer = url == null ? null : createPointer(url);
  }

  @Override
  public void setCompilerOutputPathForTests(final VirtualFile file) {
    setCompilerOutputPathForTests(file == null ? null : file.getUrl());
  }

  @Override
  public void setCompilerOutputPathForTests(final String url) {
    assertWritable();
    myCompilerOutputForTests = url;
    myCompilerOutputPathForTestsPointer = url == null ? null : createPointer(url);
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  @NotNull
  public Project getProject() {
    return myModule.getProject();
  }

  @Override
  public void inheritCompilerOutputPath(final boolean inherit) {
    if (myInheritedCompilerOutput == inherit) return;

    assertWritable();
    myInheritedCompilerOutput = inherit;
  }

  private void assertWritable() {
    assert myWritable: "Writable model can be retrieved from writable ModifiableRootModel";
  }

  @Override
  public boolean isCompilerOutputPathInherited() {
    return myInheritedCompilerOutput;
  }

  @Override
  public VirtualFilePointer getCompilerOutputPointer() {
    return myCompilerOutputPointer;
  }

  @Override
  public VirtualFilePointer getCompilerOutputForTestsPointer() {
    return myCompilerOutputPathForTestsPointer;
  }

  @Override
  public void setExcludeOutput(final boolean exclude) {
    assertWritable();
    myExcludeOutput = exclude;
  }

  @Override
  public boolean isExcludeOutput() {
    return myExcludeOutput;
  }

  @NotNull
  @Override
  public CompilerModuleExtension getModifiableModel(final boolean writable) {
    assert !myDisposed;
    return new CompilerModuleExtensionImpl(this, writable);
  }

  @Override
  public void commit() {
    if (mySource != null) {
      mySource.myCompilerOutput = myCompilerOutput;
      boolean old = mySource.myWritable;
      mySource.myWritable = true;
      mySource.setCompilerOutputPath(myCompilerOutputPointer == null ? null : myCompilerOutputPointer.getUrl());
      mySource.myCompilerOutputForTests = myCompilerOutputForTests;
      mySource.setCompilerOutputPathForTests(myCompilerOutputPathForTestsPointer == null ? null : myCompilerOutputPathForTestsPointer.getUrl());
      mySource.myInheritedCompilerOutput = myInheritedCompilerOutput;
      mySource.myExcludeOutput = myExcludeOutput;
      mySource.myWritable = old;
    }
  }

  @Override
  public boolean isChanged() {
    if (myInheritedCompilerOutput != mySource.myInheritedCompilerOutput) {
      return true;
    }

    if (!vptrEqual(myCompilerOutputPointer, mySource.myCompilerOutputPointer)) {
      return true;
    }
    if (!vptrEqual(myCompilerOutputPathForTestsPointer, mySource.myCompilerOutputPathForTestsPointer)) {
      return true;
    }

    return myExcludeOutput != mySource.myExcludeOutput;
  }

  private static boolean vptrEqual(VirtualFilePointer p1, VirtualFilePointer p2) {
    return Comparing.equal(p1 == null ? null : p1.getUrl(), p2 == null ? null : p2.getUrl());
  }

  @Override
  public void dispose() {
    myDisposed = true;
    mySource = null;
    myCompilerOutput = null;
    myCompilerOutputForTests = null;
  }

  @NotNull
  @Override
  public VirtualFile[] getOutputRoots(final boolean includeTests) {
    List<VirtualFile> result = new ArrayList<>();

    final VirtualFile outputPathForTests = includeTests ? getCompilerOutputPathForTests() : null;
    if (outputPathForTests != null) {
      result.add(outputPathForTests);
    }

    VirtualFile outputRoot = getCompilerOutputPath();
    if (outputRoot != null && !outputRoot.equals(outputPathForTests)) {
      result.add(outputRoot);
    }
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @NotNull
  @Override
  public String[] getOutputRootUrls(final boolean includeTests) {
    final List<String> result = new ArrayList<>();

    final String outputPathForTests = includeTests ? getCompilerOutputUrlForTests() : null;
    if (outputPathForTests != null) {
      result.add(outputPathForTests);
    }

    String outputRoot = getCompilerOutputUrl();
    if (outputRoot != null && !outputRoot.equals(outputPathForTests)) {
      result.add(outputRoot);
    }
    return ArrayUtil.toStringArray(result);
  }
}