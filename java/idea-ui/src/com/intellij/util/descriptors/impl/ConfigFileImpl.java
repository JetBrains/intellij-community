/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/**
 * @author nik
 */
public class ConfigFileImpl extends SimpleModificationTracker implements ConfigFile {
  @NotNull private ConfigFileInfo myInfo;
  private final VirtualFilePointer myFilePointer;
  private volatile Reference<PsiFile> myPsiFile;
  private final ConfigFileContainerImpl myContainer;
  private final Project myProject;

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
    incModificationCount();
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
}
