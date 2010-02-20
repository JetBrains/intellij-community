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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 *  @author dsl
 */
public class ContentEntryImpl extends RootModelComponentBase implements ContentEntry, ClonableContentEntry, Comparable<ContentEntryImpl> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.SimpleContentEntryImpl");
  private final VirtualFilePointer myRoot;
  @NonNls public static final String ELEMENT_NAME = "content";
  private final LinkedHashSet<SourceFolder> mySourceFolders = new LinkedHashSet<SourceFolder>();
  private final TreeSet<ExcludeFolder> myExcludeFolders = new TreeSet<ExcludeFolder>(ContentFolderComparator.INSTANCE);
  private final TreeSet<ExcludedOutputFolder> myExcludedOutputFolders = new TreeSet<ExcludedOutputFolder>(ContentFolderComparator.INSTANCE);
  @NonNls public static final String URL_ATTRIBUTE = "url";

  ContentEntryImpl(VirtualFile file, RootModelImpl m) {
    this(file.getUrl(), m);
  }

  ContentEntryImpl(String url, RootModelImpl m) {
    super(m);
    myRoot = VirtualFilePointerManager.getInstance().create(url, this, m.myVirtualFilePointerListener);
  }

  ContentEntryImpl(Element e, RootModelImpl m) throws InvalidDataException {
    this(getUrlFrom(e), m);
    initSourceFolders(e);
    initExcludeFolders(e);
  }

  private static String getUrlFrom(Element e) throws InvalidDataException {
    LOG.assertTrue(ELEMENT_NAME.equals(e.getName()));

    String url = e.getAttributeValue(URL_ATTRIBUTE);
    if (url == null) throw new InvalidDataException();
    return url;
  }

  private void initSourceFolders(Element e) throws InvalidDataException {
    mySourceFolders.clear();
    for (Object child : e.getChildren(SourceFolderImpl.ELEMENT_NAME)) {
      mySourceFolders.add(new SourceFolderImpl((Element)child, this));
    }
  }

  private void initExcludeFolders(Element e) throws InvalidDataException {
    myExcludeFolders.clear();
    for (Object child : e.getChildren(ExcludeFolderImpl.ELEMENT_NAME)) {
      myExcludeFolders.add(new ExcludeFolderImpl((Element)child, this));
    }
  }

  public VirtualFile getFile() {
    //assert !isDisposed();
    final VirtualFile file = myRoot.getFile();
    return file == null || !file.isDirectory() ? null : file;
  }

  @NotNull
  public String getUrl() {
    return myRoot.getUrl();
  }

  public SourceFolder[] getSourceFolders() {
    return mySourceFolders.toArray(new SourceFolder[mySourceFolders.size()]);
  }

  public VirtualFile[] getSourceFolderFiles() {
    assert !isDisposed();
    final SourceFolder[] sourceFolders = getSourceFolders();
    ArrayList<VirtualFile> result = new ArrayList<VirtualFile>(sourceFolders.length);
    for (SourceFolder sourceFolder : sourceFolders) {
      final VirtualFile file = sourceFolder.getFile();
      if (file != null) {
        result.add(file);
      }
    }
    return VfsUtil.toVirtualFileArray(result);
  }

  public ExcludeFolder[] getExcludeFolders() {
    //assert !isDisposed();
    final ArrayList<ExcludeFolder> result = new ArrayList<ExcludeFolder>(myExcludeFolders);
    for (DirectoryIndexExcludePolicy excludePolicy : Extensions.getExtensions(DirectoryIndexExcludePolicy.EP_NAME, getRootModel().getProject())) {
      final VirtualFilePointer[] files = excludePolicy.getExcludeRootsForModule(getRootModel());
      for (VirtualFilePointer file : files) {
        addExcludeForOutputPath(file, result);
      }
    }
    if (getRootModel().isExcludeExplodedDirectory()) {
      addExcludeForOutputPath(getRootModel().myExplodedDirectoryPointer, result);
    }
    return result.toArray(new ExcludeFolder[result.size()]);
  }

  private void addExcludeForOutputPath(final VirtualFilePointer outputPath, ArrayList<ExcludeFolder> result) {
    if (outputPath == null) return;
    final VirtualFile outputPathFile = outputPath.getFile();
    final VirtualFile file = myRoot.getFile();
    if (outputPathFile != null && file != null /* TODO: ??? && VfsUtil.isAncestor(file, outputPathFile, false) */) {
      result.add(new ExcludedOutputFolderImpl(this, outputPath));
    }
  }

  public VirtualFile[] getExcludeFolderFiles() {
    assert !isDisposed();
    final ExcludeFolder[] excludeFolders = getExcludeFolders();
    ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    for (ExcludeFolder excludeFolder : excludeFolders) {
      final VirtualFile file = excludeFolder.getFile();
      if (file != null) {
        result.add(file);
      }
    }
    return VfsUtil.toVirtualFileArray(result);
  }

  public SourceFolder addSourceFolder(@NotNull VirtualFile file, boolean isTestSource) {
    assertCanAddFolder(file);
    return addSourceFolder(new SourceFolderImpl(file, isTestSource, this));
  }

  public SourceFolder addSourceFolder(@NotNull VirtualFile file, boolean isTestSource, @NotNull String packagePrefix) {
    assertCanAddFolder(file);
    return addSourceFolder(new SourceFolderImpl(file, isTestSource, packagePrefix, this));
  }

  public SourceFolder addSourceFolder(@NotNull String url, boolean isTestSource) {
    assertFolderUnderMe(url);
    return addSourceFolder(new SourceFolderImpl(url, isTestSource, this));
  }

  private SourceFolder addSourceFolder(SourceFolderImpl f) {
    mySourceFolders.add(f);
    return f;
  }

  public void removeSourceFolder(@NotNull SourceFolder sourceFolder) {
    assert !isDisposed();
    assertCanRemoveFrom(sourceFolder, mySourceFolders);
    mySourceFolders.remove(sourceFolder);
  }

  public ExcludeFolder addExcludeFolder(@NotNull VirtualFile file) {
    assert !isDisposed();
    assertCanAddFolder(file);
    return addExcludeFolder(new ExcludeFolderImpl(file, this));
  }

  public ExcludeFolder addExcludeFolder(@NotNull String url) {
    assert !isDisposed();
    assertCanAddFolder(url);
    return addExcludeFolder(new ExcludeFolderImpl(url, this));
  }

  private void assertCanAddFolder(VirtualFile file) {
    assertCanAddFolder(file.getUrl());
  }

  private void assertCanAddFolder(String url) {
    getRootModel().assertWritable();
    assertFolderUnderMe(url);
  }

  public void removeExcludeFolder(@NotNull ExcludeFolder excludeFolder) {
    assert !isDisposed();
    assertCanRemoveFrom(excludeFolder, myExcludeFolders);
    myExcludeFolders.remove(excludeFolder);
  }

  private ExcludeFolder addExcludeFolder(ExcludeFolder f) {
    myExcludeFolders.add(f);
    return f;
  }

  private <T extends ContentFolder> void assertCanRemoveFrom(T f, Set<T> ff) {
    getRootModel().assertWritable();
    LOG.assertTrue(ff.contains(f));
  }

  private void assertFolderUnderMe(String url) {
    final String rootUrl = getUrl();
    try {
      if (!FileUtil.isAncestor(new File(rootUrl), new File(url), false)) {
        LOG.error("The file " + url + " is not under content entry root " + rootUrl);
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public boolean isSynthetic() {
    return false;
  }

  public ContentEntry cloneEntry(RootModelImpl rootModel) {
    assert !isDisposed();
    ContentEntryImpl cloned = new ContentEntryImpl(myRoot.getUrl(), rootModel);
    for (final SourceFolder sourceFolder : mySourceFolders) {
      if (sourceFolder instanceof ClonableContentFolder) {
        ContentFolder folder = ((ClonableContentFolder)sourceFolder).cloneFolder(cloned);
        cloned.mySourceFolders.add((SourceFolder)folder);
      }
    }

    for (final ExcludeFolder excludeFolder : myExcludeFolders) {
      if (excludeFolder instanceof ClonableContentFolder) {
        ContentFolder folder = ((ClonableContentFolder)excludeFolder).cloneFolder(cloned);
        cloned.myExcludeFolders.add((ExcludeFolder)folder);
      }
    }

    for (ExcludedOutputFolder excludedOutputFolder : myExcludedOutputFolders) {
      if (excludedOutputFolder instanceof ClonableContentFolder) {
        ContentFolder folder = ((ClonableContentFolder)excludedOutputFolder).cloneFolder(cloned);
        cloned.myExcludedOutputFolders.add((ExcludedOutputFolder)folder);
      }
    }

    return cloned;
  }

  public void dispose() {
    super.dispose();
    for (final Object mySourceFolder : mySourceFolders) {
      ContentFolder contentFolder = (ContentFolder)mySourceFolder;
      Disposer.dispose((Disposable)contentFolder);
    }
    for (final ExcludeFolder excludeFolder : myExcludeFolders) {
      Disposer.dispose((Disposable)excludeFolder);
    }
  }

  @Override
  protected void projectOpened() {
    super.projectOpened();
    for (SourceFolder sourceFolder : mySourceFolders) {
      ((RootModelComponentBase)sourceFolder).projectOpened();
    }
    for (ExcludeFolder excludeFolder : myExcludeFolders) {
      ((RootModelComponentBase)excludeFolder).projectOpened();
    }
    for (ExcludedOutputFolder excludedOutputFolder : myExcludedOutputFolders) {
      ((RootModelComponentBase)excludedOutputFolder).projectOpened();
    }
  }

  @Override
  protected void projectClosed() {
    super.projectClosed();
    for (SourceFolder sourceFolder : mySourceFolders) {
      ((RootModelComponentBase)sourceFolder).projectClosed();
    }
    for (ExcludeFolder excludeFolder : myExcludeFolders) {
      ((RootModelComponentBase)excludeFolder).projectClosed();
    }
    for (ExcludedOutputFolder excludedOutputFolder : myExcludedOutputFolders) {
      ((RootModelComponentBase)excludedOutputFolder).projectClosed();
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    assert !isDisposed();
    LOG.assertTrue(ELEMENT_NAME.equals(element.getName()));
    element.setAttribute(URL_ATTRIBUTE, myRoot.getUrl());
    for (final SourceFolder sourceFolder : mySourceFolders) {
      if (sourceFolder instanceof SourceFolderImpl) {
        final Element subElement = new Element(SourceFolderImpl.ELEMENT_NAME);
        ((SourceFolderImpl)sourceFolder).writeExternal(subElement);
        element.addContent(subElement);
      }
    }

    for (final ExcludeFolder excludeFolder : myExcludeFolders) {
      if (excludeFolder instanceof ExcludeFolderImpl) {
        final Element subElement = new Element(ExcludeFolderImpl.ELEMENT_NAME);
        ((ExcludeFolderImpl)excludeFolder).writeExternal(subElement);
        element.addContent(subElement);
      }
    }
  }

  //private static final class ContentFolderComparator implements Comparator<ContentFolder> {
  //  public static final ContentFolderComparator INSTANCE = new ContentFolderComparator();
  //
  //  public int compare(ContentFolder o1, ContentFolder o2) {
  //    if (o1 instanceof ContentFolderBaseImpl && o2 instanceof ContentFolderBaseImpl) {
  //      ((ContentFolderBaseImpl)o1).compareTo((ContentFolderBaseImpl)o2);
  //    }
  //    int i = o1.getUrl().compareTo(o2.getUrl());
  //    if (i != 0) return i;
  //    return System.identityHashCode(o1) - System.identityHashCode(o2);
  //  }
  //}
  private static final class ContentFolderComparator implements Comparator<ContentFolder> {
    public static final ContentFolderComparator INSTANCE = new ContentFolderComparator();

    public int compare(ContentFolder o1, ContentFolder o2) {
      return o1.getUrl().compareTo(o2.getUrl());
    }
  }

  public int compareTo(ContentEntryImpl other) {
    int i = getUrl().compareTo(other.getUrl());
    if (i != 0) return i;
    i = ArrayUtil.lexicographicCompare(getSourceFolders(), other.getSourceFolders());
    if (i != 0) return i;
    i = ArrayUtil.lexicographicCompare(getExcludeFolders(), other.getExcludeFolders());
    if (i != 0) return i;

    ExcludedOutputFolder[] excludedOutputFolders = myExcludedOutputFolders.toArray(new ExcludedOutputFolder[myExcludedOutputFolders.size()]);
    ExcludedOutputFolder[] otherExcludedOutputFolders = other.myExcludedOutputFolders.toArray(new ExcludedOutputFolder[other.myExcludedOutputFolders.size()]);
    return ArrayUtil.lexicographicCompare(excludedOutputFolders, otherExcludedOutputFolders);
  }
}
