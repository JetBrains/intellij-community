package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.projectRoots.ex.ProjectRoot;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.io.File;

/**
 * @author mike
 */
public class SimpleProjectRoot implements ProjectRoot, JDOMExternalizable {
  private String myUrl;
  private VirtualFile myFile;
  private VirtualFile[] myFileArrray = new VirtualFile[1];
  private boolean myInitialized = false;
  @NonNls private static final String ATTRIBUTE_URL = "url";

  SimpleProjectRoot(VirtualFile file) {
    myFile = file;
    myUrl = myFile.getUrl();
  }

  public SimpleProjectRoot(String url) {
    myUrl = url;
  }

  SimpleProjectRoot() {
  }

  public VirtualFile getFile() {
    return myFile;
  }

  public String getPresentableString() {
    String path = VirtualFileManager.extractPath(myUrl);
    if (path.endsWith(JarFileSystem.JAR_SEPARATOR)) {
      path = path.substring(0, path.length() - JarFileSystem.JAR_SEPARATOR.length());
    }
    return path.replace('/', File.separatorChar);
  }

  public VirtualFile[] getVirtualFiles() {
    if (!myInitialized) initialize();

    if (myFile == null) {
      return VirtualFile.EMPTY_ARRAY;
    }

    myFileArrray[0] = myFile;
    return myFileArrray;
  }

  public boolean isValid() {
    if (!myInitialized) {
      initialize();
    }

    return myFile != null && myFile.isValid();
  }

  public void update() {
    if (!myInitialized) {
      initialize();
    }

    if (myFile == null || !myFile.isValid()) {
      myFile = VirtualFileManager.getInstance().findFileByUrl(myUrl);
      if (myFile != null && !myFile.isDirectory()) myFile = null;
    }
  }

  private void initialize() {
    myInitialized = true;

    if (myFile == null || !myFile.isValid()) {
      myFile = VirtualFileManager.getInstance().findFileByUrl(myUrl);
      if (myFile != null && !myFile.isDirectory()) {
        myFile = null;
      }
    }
  }

  public String getUrl() {
    return myUrl;
  }

  public void readExternal(Element element) throws InvalidDataException {
    myUrl = element.getAttributeValue(ATTRIBUTE_URL);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    if (!myInitialized) {
      initialize();
    }

    element.setAttribute(ATTRIBUTE_URL, myUrl);
  }

}
