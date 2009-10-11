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

import com.intellij.compiler.ant.Generator;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.*;
import com.intellij.packaging.impl.ui.FileCopyPresentation;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.util.PathUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class FileCopyPackagingElement extends FileOrDirectoryCopyPackagingElement<FileCopyPackagingElement> implements RenameablePackagingElement {
  @NonNls public static final String OUTPUT_FILE_NAME_ATTRIBUTE = "output-file-name";
  private String myRenamedOutputFileName;

  public FileCopyPackagingElement() {
    super(PackagingElementFactoryImpl.FILE_COPY_ELEMENT_TYPE);
  }

  public FileCopyPackagingElement(String filePath) {
    this();
    myFilePath = filePath;
  }

  public FileCopyPackagingElement(String filePath, String outputFileName) {
    this(filePath);
    myRenamedOutputFileName = outputFileName;
  }

  public PackagingElementPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    return new FileCopyPresentation(myFilePath, getOutputFileName());
  }

  @Override
  public List<? extends Generator> computeAntInstructions(@NotNull PackagingElementResolvingContext resolvingContext, @NotNull AntCopyInstructionCreator creator,
                                                          @NotNull ArtifactAntGenerationContext generationContext,
                                                          @NotNull ArtifactType artifactType) {
    if (isDirectory()) {
      return Collections.emptyList();
    }
    final String path = generationContext.getSubstitutedPath(myFilePath);
    return Collections.singletonList((Generator)creator.createFileCopyInstruction(path, getOutputFileName()));
  }

  public String getOutputFileName() {
    return myRenamedOutputFileName != null ? myRenamedOutputFileName : PathUtil.getFileName(myFilePath);
  }

  @Override
  public void computeIncrementalCompilerInstructions(@NotNull IncrementalCompilerInstructionCreator creator,
                                                     @NotNull PackagingElementResolvingContext resolvingContext,
                                                     @NotNull ArtifactIncrementalCompilerContext compilerContext, @NotNull ArtifactType artifactType) {
    final VirtualFile file = findFile();
    if (file != null && file.isValid() && !file.isDirectory()) {
      creator.addFileCopyInstruction(file, getOutputFileName());
    }
  }

  @NonNls @Override
  public String toString() {
    return "file:" + myFilePath + (myRenamedOutputFileName != null ? ",rename to:" + myRenamedOutputFileName : "");
  }

  public boolean isDirectory() {
    return new File(FileUtil.toSystemDependentName(myFilePath)).isDirectory();
  }


  @Override
  public boolean isEqualTo(@NotNull PackagingElement<?> element) {
    return element instanceof FileCopyPackagingElement && super.isEqualTo(element)
           && Comparing.equal(myRenamedOutputFileName, ((FileCopyPackagingElement)element).getRenamedOutputFileName());
  }

  public FileCopyPackagingElement getState() {
    return this;
  }

  public void loadState(FileCopyPackagingElement state) {
    setFilePath(state.getFilePath());
    setRenamedOutputFileName(state.getRenamedOutputFileName());
  }

  @Nullable
  @Attribute(OUTPUT_FILE_NAME_ATTRIBUTE)
  public String getRenamedOutputFileName() {
    return myRenamedOutputFileName;
  }

  public void setRenamedOutputFileName(String renamedOutputFileName) {
    myRenamedOutputFileName = renamedOutputFileName;
  }

  public String getName() {
    return getOutputFileName();
  }

  public boolean canBeRenamed() {
    return !isDirectory();
  }

  public void rename(@NotNull String newName) {
    myRenamedOutputFileName = newName.equals(PathUtil.getFileName(myFilePath)) ? null : newName;
  }

  @Nullable
  public VirtualFile getLibraryRoot() {
    final String url = VfsUtil.getUrlForLibraryRoot(new File(FileUtil.toSystemDependentName(getFilePath())));
    return VirtualFileManager.getInstance().findFileByUrl(url);
  }
}
