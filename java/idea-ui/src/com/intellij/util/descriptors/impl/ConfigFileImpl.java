package com.intellij.util.descriptors.impl;

import com.intellij.openapi.project.Project;
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

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;

/**
 * @author nik
 */
public class ConfigFileImpl implements ConfigFile {
  @NotNull private ConfigFileInfo myInfo;
  private final VirtualFilePointer myFilePointer;
  private volatile Reference<PsiFile> myPsiFile;
  private final ConfigFileContainerImpl myContainer;
  private final Project myProject;
  private long myModificationCount;

  public ConfigFileImpl(@NotNull final ConfigFileContainerImpl container, @NotNull final ConfigFileInfo configuration) {
    myContainer = container;
    myInfo = configuration;
    final VirtualFilePointerManager pointerManager = VirtualFilePointerManager.getInstance();
    myFilePointer = pointerManager.create(configuration.getUrl(), this, new VirtualFilePointerListener() {
      @Override
      public void beforeValidityChanged(@NotNull final VirtualFilePointer[] pointers) {
      }

      @Override
      public void validityChanged(@NotNull final VirtualFilePointer[] pointers) {
        myPsiFile = null;
        onChange();
      }
    });
    onChange();
    myProject = myContainer.getProject();
  }

  private void onChange() {
    myModificationCount++;
    myContainer.fireDescriptorChanged(this);
  }

  @Override
  public String getUrl() {
    return myFilePointer.getUrl();
  }

  public void setInfo(@NotNull final ConfigFileInfo info) {
    myInfo = info;
  }

  @Override
  @Nullable
  public VirtualFile getVirtualFile() {
    return myFilePointer.isValid() ? myFilePointer.getFile() : null;
  }

  @Override
  @Nullable
  public PsiFile getPsiFile() {
    Reference<PsiFile> ref = myPsiFile;
    PsiFile psiFile = ref == null ? null : ref.get();

    if (psiFile != null && psiFile.isValid()) {
      return psiFile;
    }

    VirtualFile virtualFile = getVirtualFile();
    if (virtualFile == null || !virtualFile.isValid()) return null;

    psiFile = PsiManager.getInstance(myProject).findFile(virtualFile);

    myPsiFile = new SoftReference<PsiFile>(psiFile);

    return psiFile;
  }

  @Override
  @Nullable
  public XmlFile getXmlFile() {
    final PsiFile file = getPsiFile();
    return file instanceof XmlFile ? (XmlFile)file : null;
  }

  @Override
  public void dispose() {
  }

  @Override
  @NotNull
  public ConfigFileInfo getInfo() {
    return myInfo;
  }

  @Override
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


  @Override
  @NotNull
  public ConfigFileMetaData getMetaData() {
    return myInfo.getMetaData();
  }


  @Override
  public long getModificationCount() {
    return myModificationCount;
  }
}
