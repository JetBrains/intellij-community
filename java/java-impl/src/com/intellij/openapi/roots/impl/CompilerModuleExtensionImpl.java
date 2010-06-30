/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 27-Dec-2007
 */
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CompilerModuleExtensionImpl extends CompilerModuleExtension {
  @NonNls private static final String OUTPUT_TAG = "output";
  @NonNls private static final String TEST_OUTPUT_TAG = "output-test";
  @NonNls private static final String ATTRIBUTE_URL = "url";
  @NonNls private static final String INHERIT_COMPILER_OUTPUT = "inherit-compiler-output";
  @NonNls private static final String EXCLUDE_OUTPUT_TAG = "exclude-output";

  private String myCompilerOutput;
  private VirtualFilePointer myCompilerOutputPointer;

  private String myCompilerOutputForTests;
  private VirtualFilePointer myCompilerOutputPathForTestsPointer;

  private boolean myInheritedCompilerOutput = true;
  private boolean myExcludeOutput = true;
  private final Module myModule;

  private CompilerModuleExtensionImpl mySource;
  private boolean myWritable;
  private boolean myDisposed;

  public CompilerModuleExtensionImpl(@NotNull final Module module) {
    myModule = module;
  }

  public CompilerModuleExtensionImpl(final CompilerModuleExtensionImpl source, final boolean writable) {
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
    final VirtualFilePointerListener listener =
        ((ProjectRootManagerImpl)ProjectRootManager.getInstance(myModule.getProject())).getVirtualFilePointerListener();
    final VirtualFilePointerManager filePointerManager = VirtualFilePointerManager.getInstance();
    return filePointerManager.duplicate(pointer, this, listener);
  }


  public void readExternal(final Element element) throws InvalidDataException {
    assert !myDisposed;
    final String value = element.getAttributeValue(INHERIT_COMPILER_OUTPUT);
    myInheritedCompilerOutput = value != null && Boolean.parseBoolean(value);
    myExcludeOutput = element.getChild(EXCLUDE_OUTPUT_TAG) != null;

    myCompilerOutputPointer = getOutputPathValue(element, OUTPUT_TAG, !myInheritedCompilerOutput);

    myCompilerOutput = getOutputPathValue(element, OUTPUT_TAG);

    myCompilerOutputPathForTestsPointer = getOutputPathValue(element, TEST_OUTPUT_TAG, !myInheritedCompilerOutput);

    myCompilerOutputForTests = getOutputPathValue(element, TEST_OUTPUT_TAG);
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    assert !myDisposed;
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
    element.setAttribute(INHERIT_COMPILER_OUTPUT, String.valueOf(myInheritedCompilerOutput));
    if (myExcludeOutput) {
      element.addContent(new Element(EXCLUDE_OUTPUT_TAG));
    }
  }

  @Nullable
  protected VirtualFilePointer getOutputPathValue(Element element, String tag, final boolean createPointer) {
    final Element outputPathChild = element.getChild(tag);
    VirtualFilePointer vptr = null;
    if (outputPathChild != null && createPointer) {
      String outputPath = outputPathChild.getAttributeValue(ATTRIBUTE_URL);
      vptr = createPointer(outputPath);
    }
    return vptr;
  }

  @Nullable
  protected static String getOutputPathValue(Element element, String tag) {
    final Element outputPathChild = element.getChild(tag);
    if (outputPathChild != null) {
      return outputPathChild.getAttributeValue(ATTRIBUTE_URL);
    }
    return null;
  }

  @Nullable
  public VirtualFile getCompilerOutputPath() {
    if (myInheritedCompilerOutput) {
      final VirtualFile projectOutputPath = CompilerProjectExtension.getInstance(getProject()).getCompilerOutput();
      if (projectOutputPath == null) return null;
      return projectOutputPath.findFileByRelativePath(PRODUCTION + "/" + getModule().getName());
    }
    return myCompilerOutputPointer == null ? null : myCompilerOutputPointer.getFile();
  }

  @Nullable
  public VirtualFile getCompilerOutputPathForTests() {
    if (myInheritedCompilerOutput) {
      final VirtualFile projectOutputPath = CompilerProjectExtension.getInstance(getProject()).getCompilerOutput();
      if (projectOutputPath == null) return null;
      return projectOutputPath.findFileByRelativePath(TEST + "/" + getModule().getName());
    }
    return myCompilerOutputPathForTestsPointer == null ? null : myCompilerOutputPathForTestsPointer.getFile();
  }

  @Nullable
  public String getCompilerOutputUrl() {
    if (myInheritedCompilerOutput) {
      final String projectOutputPath = CompilerProjectExtension.getInstance(getProject()).getCompilerOutputUrl();
      if (projectOutputPath == null) return null;
      return projectOutputPath + "/" + PRODUCTION + "/" + getModule().getName();
    }
    return myCompilerOutputPointer == null ? null : myCompilerOutputPointer.getUrl();
  }

  @Nullable
  public String getCompilerOutputUrlForTests() {
    if (myInheritedCompilerOutput) {
      final String projectOutputPath = CompilerProjectExtension.getInstance(getProject()).getCompilerOutputUrl();
      if (projectOutputPath == null) return null;
      return projectOutputPath + "/" + TEST + "/" + getModule().getName();
    }
    return myCompilerOutputPathForTestsPointer == null ? null : myCompilerOutputPathForTestsPointer.getUrl();
  }

  public void setCompilerOutputPath(final VirtualFile file) {
    setCompilerOutputPath(file == null ? null : file.getUrl());
  }

  private VirtualFilePointer createPointer(final String url) {
    final VirtualFilePointerListener listener =
      ((ProjectRootManagerImpl)ProjectRootManager.getInstance(myModule.getProject())).getVirtualFilePointerListener();
    return VirtualFilePointerManager.getInstance().create(url, this, listener);
  }

  public void setCompilerOutputPath(final String url) {
    assertWritable();
    myCompilerOutput = url;
    myCompilerOutputPointer = url == null ? null : createPointer(url);
  }

  public void setCompilerOutputPathForTests(final VirtualFile file) {
    setCompilerOutputPathForTests(file == null ? null : file.getUrl());
  }

  public void setCompilerOutputPathForTests(final String url) {
    assertWritable();
    myCompilerOutputForTests = url;
    myCompilerOutputPathForTestsPointer = url == null ? null : createPointer(url);
  }

  public Module getModule() {
    return myModule;
  }

  public Project getProject() {
    return myModule.getProject();
  }

  public void inheritCompilerOutputPath(final boolean inherit) {
    assertWritable();
    myInheritedCompilerOutput = inherit;
  }

  private void assertWritable() {
    assert myWritable: "Writable model can be retrieved from writable ModifiableRootModel";
  }

  public boolean isCompilerOutputPathInherited() {
    return myInheritedCompilerOutput;
  }

  public VirtualFilePointer getCompilerOutputPointer() {
    return myCompilerOutputPointer;
  }

  public VirtualFilePointer getCompilerOutputForTestsPointer() {
    return myCompilerOutputPathForTestsPointer;
  }

  public void setExcludeOutput(final boolean exclude) {
    assertWritable();
    myExcludeOutput = exclude;
  }

  public boolean isExcludeOutput() {
    return myExcludeOutput;
  }

  public CompilerModuleExtension getModifiableModel(final boolean writable) {
    assert !myDisposed;
    return new CompilerModuleExtensionImpl(this, writable);
  }

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

  public boolean isChanged() {
    if (myInheritedCompilerOutput != mySource.myInheritedCompilerOutput) {
      return true;
    }

    if (!myInheritedCompilerOutput) {
      if (!vptrEqual(myCompilerOutputPointer, mySource.myCompilerOutputPointer)) {
        return true;
      }
      if (!vptrEqual(myCompilerOutputPathForTestsPointer, mySource.myCompilerOutputPathForTestsPointer)) {
        return true;
      }
    }

    return myExcludeOutput != mySource.myExcludeOutput;
  }

  private static boolean vptrEqual(VirtualFilePointer p1, VirtualFilePointer p2) {
    return Comparing.equal(p1 == null ? null : p1.getUrl(), p2 == null ? null : p2.getUrl());
  }

  public void dispose() {
    myDisposed = true;
    mySource = null;
    myCompilerOutput = null;
    myCompilerOutputForTests = null;
  }

  @Nullable
  @Override
  public VirtualFile[] getRootPaths(final OrderRootType type) {
    if (OrderRootType.CLASSES_AND_OUTPUT.equals(type) ||
        OrderRootType.COMPILATION_CLASSES.equals(type) || 
        OrderRootType.PRODUCTION_COMPILATION_CLASSES.equals(type)) {
      final ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();

      final VirtualFile outputPathForTests = OrderRootType.PRODUCTION_COMPILATION_CLASSES.equals(type) ? null : getCompilerOutputPathForTests();
      if (outputPathForTests != null) {
        result.add(outputPathForTests);
      }

      VirtualFile outputRoot = getCompilerOutputPath();
      if (outputRoot != null && !outputRoot.equals(outputPathForTests)) {
        result.add(outputRoot);
      }
      return VfsUtil.toVirtualFileArray(result);
    }
    return null;
  }

  @Override
  public String[] getRootUrls(final OrderRootType type) {
    if (OrderRootType.CLASSES_AND_OUTPUT.equals(type) ||
        OrderRootType.COMPILATION_CLASSES.equals(type) ||
        OrderRootType.PRODUCTION_COMPILATION_CLASSES.equals(type)) {
      final List<String> result = new ArrayList<String>();

      final String outputPathForTests = OrderRootType.PRODUCTION_COMPILATION_CLASSES.equals(type) ? null : getCompilerOutputUrlForTests();
      if (outputPathForTests != null) {
        result.add(outputPathForTests);
      }

      String outputRoot = getCompilerOutputUrl();
      if (outputRoot != null && !outputRoot.equals(outputPathForTests)) {
        result.add(outputRoot);
      }
      return ArrayUtil.toStringArray(result);
    }
    return null;
  }
}