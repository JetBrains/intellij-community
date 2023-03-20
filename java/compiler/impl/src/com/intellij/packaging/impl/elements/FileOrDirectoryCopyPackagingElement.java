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
package com.intellij.packaging.impl.elements;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementOutputKind;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.workspaceModel.ide.VirtualFileUrls;
import com.intellij.workspaceModel.storage.bridgeEntities.FileOrDirectoryPackagingElementEntity;
import com.intellij.workspaceModel.storage.url.VirtualFileUrl;
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager;
import kotlin.Unit;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.util.JpsPathUtil;

import java.util.Objects;

public abstract class FileOrDirectoryCopyPackagingElement<T extends FileOrDirectoryCopyPackagingElement> extends PackagingElement<T> {
  @NonNls public static final String PATH_ATTRIBUTE = "path";
  protected String myFilePath;

  public FileOrDirectoryCopyPackagingElement(PackagingElementType type) {
    super(type);
  }

  protected FileOrDirectoryCopyPackagingElement(PackagingElementType type, String filePath) {
    super(type);
    myFilePath = filePath;
  }

  @Nullable
  public VirtualFile findFile() {
    return LocalFileSystem.getInstance().findFileByPath(myFilePath);
  }

  @Override
  public boolean isEqualTo(@NotNull PackagingElement<?> element) {
    return element instanceof FileOrDirectoryCopyPackagingElement &&
           getMyFilePath() != null &&
           getMyFilePath().equals(((FileOrDirectoryCopyPackagingElement<?>)element).getFilePath());
  }

  @Attribute(PATH_ATTRIBUTE)
  public String getFilePath() {
    return getMyFilePath();
  }

  public void setFilePath(String filePath) {
    String filePathBefore = getMyFilePath();
    this.update(
      () -> myFilePath = filePath,
      (builder, entity) -> {
        if (filePathBefore.equals(filePath)) return;

        builder.modifyEntity(FileOrDirectoryPackagingElementEntity.Builder.class, entity, ent -> {
          VirtualFileUrlManager manager = VirtualFileUrls.getVirtualFileUrlManager(myProject);
          if (filePath != null) {
            VirtualFileUrl fileUrl = manager.fromPath(filePath);
            ent.setFilePath(fileUrl);
          }
          else {
            ent.setFilePath(null);
          }
          return Unit.INSTANCE;
        });
      });
  }

  @NotNull
  @Override
  public PackagingElementOutputKind getFilesKind(PackagingElementResolvingContext context) {
    VirtualFile file = findFile();
    if (file == null) return PackagingElementOutputKind.OTHER;

    if (file.isDirectory() && file.isInLocalFileSystem()) {
      boolean containsDirectories = false;
      boolean containsJars = false;
      for (VirtualFile child : file.getChildren()) {
        if (child.isDirectory() && child.isInLocalFileSystem()) {
          containsDirectories = true;
        }
        else {
          containsJars |= isJar(child);
        }
        if (containsDirectories && containsJars) break;
      }
      return new PackagingElementOutputKind(containsDirectories, containsJars);
    }
    return isJar(file) ? PackagingElementOutputKind.JAR_FILES : PackagingElementOutputKind.OTHER;
  }

  private static boolean isJar(VirtualFile file) {
    final String ext = file.getExtension();
    return ext != null && ext.equalsIgnoreCase("jar");
  }

  protected String getMyFilePath() {
    if (myStorage == null) {
      return myFilePath;
    } else {
      FileOrDirectoryPackagingElementEntity entity = (FileOrDirectoryPackagingElementEntity)getThisEntity();
      String path = JpsPathUtil.urlToPath(entity.getFilePath().getUrl());
      if (!Objects.equals(myFilePath, path)) {
        myFilePath = path;
      }
      return path;
    }
  }
}
