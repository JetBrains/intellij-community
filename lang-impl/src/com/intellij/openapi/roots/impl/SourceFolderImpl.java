package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ContentFolder;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 *  @author dsl
 */
public class SourceFolderImpl extends ContentFolderBaseImpl implements SourceFolder, ClonableContentFolder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.SimpleSourceFolderImpl");
  private boolean myIsTestSource;
  @NonNls static final String ELEMENT_NAME = "sourceFolder";
  @NonNls private static final String TEST_SOURCE_ATTR = "isTestSource";
  private String myPackagePrefix;
  static final String DEFAULT_PACKAGE_PREFIX = "";
  @NonNls private static final String PACKAGE_PREFIX_ATTR = "packagePrefix";

  SourceFolderImpl(@NotNull VirtualFile file, boolean isTestSource, @NotNull ContentEntryImpl contentEntry) {
    this(file, isTestSource, DEFAULT_PACKAGE_PREFIX, contentEntry);
  }

  SourceFolderImpl(@NotNull VirtualFile file, boolean isTestSource, @NotNull String packagePrefix, @NotNull ContentEntryImpl contentEntry) {
    super(file, contentEntry);
    myIsTestSource = isTestSource;
    myPackagePrefix = packagePrefix;
  }

  public SourceFolderImpl(@NotNull String url, boolean isTestSource, @NotNull ContentEntryImpl contentEntry) {
    super(url, contentEntry);
    myIsTestSource = isTestSource;
    myPackagePrefix = DEFAULT_PACKAGE_PREFIX;
  }

  SourceFolderImpl(Element element, ContentEntryImpl contentEntry) throws InvalidDataException {
    super(element, contentEntry);
    LOG.assertTrue(element.getName().equals(ELEMENT_NAME));
    final String testSource = element.getAttributeValue(TEST_SOURCE_ATTR);
    if (testSource == null) throw new InvalidDataException();
    myIsTestSource = Boolean.valueOf(testSource).booleanValue();
    final String packagePrefix = element.getAttributeValue(PACKAGE_PREFIX_ATTR);
    if (packagePrefix != null) {
      myPackagePrefix = packagePrefix;
    }
    else {
      myPackagePrefix = DEFAULT_PACKAGE_PREFIX;
    }
  }

  private SourceFolderImpl(SourceFolderImpl that, ContentEntryImpl contentEntry) {
    super(that, contentEntry);
    myIsTestSource = that.myIsTestSource;
    myPackagePrefix = that.myPackagePrefix;
  }

  public boolean isTestSource() {
    return myIsTestSource;
  }

  public String getPackagePrefix() {
    return myPackagePrefix;
  }

  public void setPackagePrefix(String packagePrefix) {
    myPackagePrefix = packagePrefix;
  }

  void writeExternal(Element element) {
    writeFolder(element, ELEMENT_NAME);
    element.setAttribute(TEST_SOURCE_ATTR, Boolean.toString(myIsTestSource));
    if (!DEFAULT_PACKAGE_PREFIX.equals(myPackagePrefix)) {
      element.setAttribute(PACKAGE_PREFIX_ATTR, myPackagePrefix);
    }
  }

  public ContentFolder cloneFolder(ContentEntry contentEntry) {
    ContentEntryImpl clone = (ContentEntryImpl)((ClonableContentEntry)contentEntry).cloneEntry(((RootModelComponentBase)contentEntry).getRootModel());
    return new SourceFolderImpl(this, clone);
  }

  public int compareTo(ContentFolderBaseImpl folder) {
    if (!(folder instanceof SourceFolderImpl)) return -1;

    int i = super.compareTo(folder);
    if (i!= 0) return i;

    i = myPackagePrefix.compareTo(((SourceFolderImpl)folder).myPackagePrefix);
    if (i!= 0) return i;
    return Boolean.valueOf(myIsTestSource).compareTo(((SourceFolderImpl)folder).myIsTestSource);
  }
}
