package com.intellij.packaging.impl.elements;

import com.intellij.compiler.ant.BuildProperties;
import com.intellij.compiler.ant.Generator;
import com.intellij.compiler.ant.artifacts.ArchiveAntCopyInstructionCreator;
import com.intellij.compiler.ant.taskdefs.Jar;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.*;
import com.intellij.packaging.impl.ui.ArchiveElementPresentation;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class ArchivePackagingElement extends CompositeElementWithManifest<ArchivePackagingElement> {
  private String myArchiveFileName;

  public ArchivePackagingElement() {
    super(PackagingElementFactoryImpl.ARCHIVE_ELEMENT_TYPE);
  }

  public ArchivePackagingElement(String archiveFileName) {
    super(PackagingElementFactoryImpl.ARCHIVE_ELEMENT_TYPE);
    myArchiveFileName = archiveFileName;
  }

  public PackagingElementPresentation createPresentation(PackagingEditorContext context) {
    return new ArchiveElementPresentation(this);
  }

  @Override
  public List<? extends Generator> computeAntInstructions(@NotNull PackagingElementResolvingContext resolvingContext, @NotNull AntCopyInstructionCreator creator,
                                                          @NotNull ArtifactAntGenerationContext generationContext,
                                                          @NotNull ArtifactType artifactType) {
    final String tempJarProperty = generationContext.createNewTempFileProperty("temp.jar.path." + myArchiveFileName, myArchiveFileName);
    String jarPath = BuildProperties.propertyRef(tempJarProperty);
    final Jar jar = new Jar(jarPath, "preserve", true);
    for (Generator generator : computeChildrenGenerators(resolvingContext, new ArchiveAntCopyInstructionCreator(""), generationContext, artifactType)) {
      jar.add(generator);
    }
    generationContext.runBeforeCurrentArtifact(jar);
    return Collections.singletonList(creator.createFileCopyInstruction(jarPath, myArchiveFileName));
  }

  @Override
  public void computeIncrementalCompilerInstructions(@NotNull IncrementalCompilerInstructionCreator creator,
                                                     @NotNull PackagingElementResolvingContext resolvingContext,
                                                     @NotNull ArtifactIncrementalCompilerContext compilerContext, @NotNull ArtifactType artifactType) {
    computeChildrenInstructions(creator.archive(myArchiveFileName), resolvingContext, compilerContext, artifactType);
  }

  @Attribute("name")
  public String getArchiveFileName() {
    return myArchiveFileName;
  }

  @NonNls @Override
  public String toString() {
    return "archive:" + myArchiveFileName;
  }

  public ArchivePackagingElement getState() {
    return this;
  }

  public void setArchiveFileName(String archiveFileName) {
    myArchiveFileName = archiveFileName;
  }

  @Override
  public String getName() {
    return myArchiveFileName;
  }

  @Override
  public void rename(@NotNull String newName) {
    myArchiveFileName = newName;
  }

  @Override
  public boolean isEqualTo(@NotNull PackagingElement<?> element) {
    return element instanceof ArchivePackagingElement && ((ArchivePackagingElement)element).getArchiveFileName().equals(myArchiveFileName);
  }

  public void loadState(ArchivePackagingElement state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
