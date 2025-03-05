// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.descriptors.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SimpleModificationTracker;
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

public final class ConfigFileImpl extends SimpleModificationTracker implements ConfigFile {
  private @NotNull ConfigFileInfo myInfo;
  private final VirtualFilePointer myFilePointer;
  private volatile Reference<PsiFile> myPsiFile;
  private final ConfigFileContainerImpl myContainer;
  private final Project myProject;

  public ConfigFileImpl(final @NotNull ConfigFileContainerImpl container, final @NotNull ConfigFileInfo configuration) {
    myContainer = container;
    myInfo = configuration;
    final VirtualFilePointerManager pointerManager = VirtualFilePointerManager.getInstance();
    myFilePointer = pointerManager.create(configuration.getUrl(), this, new VirtualFilePointerListener() {
      @Override
      public void validityChanged(final VirtualFilePointer @NotNull [] pointers) {
        myPsiFile = null;
        onChange();
      }
    });
    onChange();
    myProject = myContainer.getProject();
  }

  private void onChange() {
    incModificationCount();
    myContainer.fireDescriptorChanged(this);
  }

  @Override
  public String getUrl() {
    return myFilePointer.getUrl();
  }

  public void setInfo(final @NotNull ConfigFileInfo info) {
    myInfo = info;
  }

  @Override
  public @Nullable VirtualFile getVirtualFile() {
    return myFilePointer.isValid() ? myFilePointer.getFile() : null;
  }

  @Override
  public @Nullable PsiFile getPsiFile() {
    PsiFile psiFile = com.intellij.reference.SoftReference.dereference(myPsiFile);

    if (psiFile != null && psiFile.isValid()) {
      return psiFile;
    }

    VirtualFile virtualFile = getVirtualFile();
    if (virtualFile == null || !virtualFile.isValid()) return null;

    psiFile = PsiManager.getInstance(myProject).findFile(virtualFile);

    myPsiFile = new SoftReference<>(psiFile);

    return psiFile;
  }

  @Override
  public @Nullable XmlFile getXmlFile() {
    final PsiFile file = getPsiFile();
    return file instanceof XmlFile ? (XmlFile)file : null;
  }

  @Override
  public void dispose() {
  }

  @Override
  public @NotNull ConfigFileInfo getInfo() {
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
  public @NotNull ConfigFileMetaData getMetaData() {
    return myInfo.getMetaData();
  }
}
