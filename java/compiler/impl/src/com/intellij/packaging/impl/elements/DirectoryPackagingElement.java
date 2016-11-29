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
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.AntCopyInstructionCreator;
import com.intellij.packaging.elements.ArtifactAntGenerationContext;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.ui.DirectoryElementPresentation;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 *
 * classpath is used for exploded WAR and EJB directories under exploded EAR
 */
public class DirectoryPackagingElement extends CompositeElementWithManifest<DirectoryPackagingElement> {
  @NonNls public static final String NAME_ATTRIBUTE = "name";
  private String myDirectoryName;

  public DirectoryPackagingElement() {
    super(PackagingElementFactoryImpl.DIRECTORY_ELEMENT_TYPE);
  }

  public DirectoryPackagingElement(String directoryName) {
    super(PackagingElementFactoryImpl.DIRECTORY_ELEMENT_TYPE);
    myDirectoryName = directoryName;
  }

  public PackagingElementPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    return new DirectoryElementPresentation(this); 
  }

  @Override
  public List<? extends Generator> computeAntInstructions(@NotNull PackagingElementResolvingContext resolvingContext, @NotNull AntCopyInstructionCreator creator,
                                                          @NotNull ArtifactAntGenerationContext generationContext,
                                                          @NotNull ArtifactType artifactType) {

    final List<Generator> children = new ArrayList<>();
    final Generator command = creator.createSubFolderCommand(myDirectoryName);
    if (command != null) {
      children.add(command);
    }
    children.addAll(computeChildrenGenerators(resolvingContext, creator.subFolder(myDirectoryName), generationContext, artifactType));
    return children;
  }

  public DirectoryPackagingElement getState() {
    return this;
  }

  @NonNls @Override
  public String toString() {
    return "dir:" + myDirectoryName;
  }

  @Attribute(NAME_ATTRIBUTE)
  public String getDirectoryName() {
    return myDirectoryName;
  }

  public void setDirectoryName(String directoryName) {
    myDirectoryName = directoryName;
  }

  public void rename(@NotNull String newName) {
    myDirectoryName = newName;
  }

  public String getName() {
    return myDirectoryName;
  }

  @Override
  public boolean isEqualTo(@NotNull PackagingElement<?> element) {
    return element instanceof DirectoryPackagingElement && ((DirectoryPackagingElement)element).getDirectoryName().equals(myDirectoryName);
  }

  public void loadState(DirectoryPackagingElement state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
