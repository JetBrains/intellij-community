/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.vfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;


public class VcsFileSystem extends DeprecatedVirtualFileSystem implements ApplicationComponent {

  private final String myProtocol;
  public static final String COULD_NOT_IMPLEMENT_MESSAGE = VcsBundle.message("exception.text.internal.errror.could.not.implement.method");

  public static VcsFileSystem getInstance() {
    return ApplicationManager.getApplication().getComponent(VcsFileSystem.class);
  }

  public VcsFileSystem() {
    //noinspection HardCodedStringLiteral
    myProtocol = "vcs";
  }

  public String getProtocol() {
    return myProtocol;
  }

  public VirtualFile findFileByPath(@NotNull String path) {
    return null;
  }

  public void refresh(boolean asynchronous) {
  }

  public VirtualFile refreshAndFindFileByPath(String path) {
    return null;
  }

  public void fireContentsChanged(Object requestor, VirtualFile file, long oldModificationStamp) {
    super.fireContentsChanged(requestor, file, oldModificationStamp);
  }

  protected void fireBeforeFileDeletion(Object requestor, VirtualFile file) {
    super.fireBeforeFileDeletion(requestor, file);
  }

  protected void fireFileDeleted(Object requestor, VirtualFile file, String fileName, VirtualFile parent) {
    super.fireFileDeleted(requestor, file, fileName, parent);
  }

  public String getComponentName() {
    return "VcsFileSystem";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  protected void fireBeforeContentsChange(Object requestor, VirtualFile file) {
    super.fireBeforeContentsChange(requestor, file);
  }

  public void forceRefreshFiles(final boolean asynchronous, VirtualFile... files) {
  }

  public void deleteFile(Object requestor, VirtualFile vFile) throws IOException {
    throw new RuntimeException(COULD_NOT_IMPLEMENT_MESSAGE);
  }

  public void moveFile(Object requestor, VirtualFile vFile, VirtualFile newParent) throws IOException {
    throw new RuntimeException(COULD_NOT_IMPLEMENT_MESSAGE);
  }

  public VirtualFile copyFile(Object requestor, VirtualFile vFile, VirtualFile newParent, final String copyName) throws IOException {
    throw new RuntimeException(COULD_NOT_IMPLEMENT_MESSAGE);
  }

  public void renameFile(Object requestor, VirtualFile vFile, String newName) throws IOException {
    throw new RuntimeException(COULD_NOT_IMPLEMENT_MESSAGE);
  }

  public VirtualFile createChildFile(Object requestor, VirtualFile vDir, String fileName) throws IOException {
    throw new RuntimeException(COULD_NOT_IMPLEMENT_MESSAGE);
  }

  public VirtualFile createChildDirectory(Object requestor, VirtualFile vDir, String dirName) throws IOException {
    throw new RuntimeException(COULD_NOT_IMPLEMENT_MESSAGE);
  }
}
