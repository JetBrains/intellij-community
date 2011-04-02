
package com.intellij.util.descriptors.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.descriptors.ConfigFile;
import com.intellij.util.descriptors.ConfigFileInfo;
import com.intellij.util.descriptors.ConfigFileMetaData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class ConfigFileImpl implements ConfigFile {
  @NotNull private ConfigFileInfo myInfo;
  private VirtualFilePointer myFilePointer;
  private PsiFile myPsiFile;
  private final ConfigFileContainerImpl myContainer;
  private final Project myProject;
  private long myModificationCount;
  private final Object myPsiFileLock = new Object();
  private final VirtualFilePointerListener myListener = new VirtualFilePointerListener() {
    public void beforeValidityChanged(final VirtualFilePointer[] pointers) {
    }

    public void validityChanged(final VirtualFilePointer[] pointers) {
      synchronized (myPsiFileLock) {
        myPsiFile = null;
      }
      onChange();
    }
  };

  public ConfigFileImpl(@NotNull final ConfigFileContainerImpl container, @NotNull final ConfigFileInfo configuration) {
    myContainer = container;
    myInfo = configuration;
    setUrl(configuration.getUrl());
    Disposer.register(container, this);
    myProject = myContainer.getProject();
  }

  private void setUrl(String url) {
    final VirtualFilePointerManager pointerManager = VirtualFilePointerManager.getInstance();
    myFilePointer = pointerManager.create(url, this, myListener);
    onChange();
  }

  private void onChange() {
    myModificationCount++;
    myContainer.fireDescriptorChanged(this);
  }

  public String getUrl() {
    return myFilePointer.getUrl();
  }

  public void setInfo(@NotNull final ConfigFileInfo info) {
    myInfo = info;
  }

  @Nullable
  public VirtualFile getVirtualFile() {
    return myFilePointer.getFile();
  }

  @Nullable
  public PsiFile getPsiFile() {
    PsiFile psiFile;
    synchronized (myPsiFileLock) {
      psiFile = myPsiFile;
    }

    if (psiFile != null && psiFile.isValid()) {
      return psiFile;
    }

    VirtualFile virtualFile = getVirtualFile();
    if (virtualFile == null || !virtualFile.isValid()) return null;

    psiFile = PsiManager.getInstance(myProject).findFile(virtualFile);

    synchronized (myPsiFileLock) {
      myPsiFile = psiFile;
    }

    return psiFile;
  }

  @Nullable
  public XmlFile getXmlFile() {
    final PsiFile file = getPsiFile();
    return file instanceof XmlFile ? (XmlFile)file : null;
  }

  public void dispose() {
  }

  @NotNull
  public ConfigFileInfo getInfo() {
    return myInfo;
  }

  public boolean isValid() {
    final PsiFile psiFile = getPsiFile();
    if (psiFile == null || !psiFile.isValid()) {
      return false;
    }
    if (psiFile instanceof XmlFile) {
      final XmlDocument document = ((XmlFile)psiFile).getDocument();
      return document != null && document.getRootTag() != null;
    }
    return true;
  }


  @NotNull
  public ConfigFileMetaData getMetaData() {
    return myInfo.getMetaData();
  }


  public long getModificationCount() {
    return myModificationCount;
  }
}
