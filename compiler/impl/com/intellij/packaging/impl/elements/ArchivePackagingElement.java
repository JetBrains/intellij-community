package com.intellij.packaging.impl.elements;

import com.intellij.compiler.ant.BuildProperties;
import com.intellij.compiler.ant.Generator;
import com.intellij.compiler.ant.artifacts.ArchiveAntCopyInstructionCreator;
import com.intellij.compiler.ant.taskdefs.Jar;
import com.intellij.packaging.elements.*;
import com.intellij.packaging.impl.ui.ArchiveElementPresentation;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class ArchivePackagingElement extends CompositePackagingElement<ArchivePackagingElement> {
  private String myArchiveFileName;
  private String myMainClass;
  private String myClasspath;

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
                                                   @NotNull ArtifactAntGenerationContext generationContext) {
    final String tempJarProperty = generationContext.createNewTempFileProperty("temp.jar.path." + myArchiveFileName, myArchiveFileName);
    String jarPath = BuildProperties.propertyRef(tempJarProperty);
    final Jar jar = new Jar(jarPath, "preserve");
    for (Generator generator : computeChildrenGenerators(resolvingContext, new ArchiveAntCopyInstructionCreator(""), generationContext)) {
      jar.add(generator);
    }
    generationContext.runBeforeCurrentArtifact(jar);
    return Collections.singletonList(creator.createFileCopyInstruction(jarPath, myArchiveFileName));
  }

  @Override
  public void computeIncrementalCompilerInstructions(@NotNull IncrementalCompilerInstructionCreator creator,
                                                     @NotNull PackagingElementResolvingContext resolvingContext,
                                                     @NotNull ArtifactIncrementalCompilerContext compilerContext) {
    computeChildrenInstructions(creator.archive(myArchiveFileName), resolvingContext, compilerContext);
  }

  public ArchivePackagingElement getState() {
    return this;
  }

  public void loadState(ArchivePackagingElement state) {
    myArchiveFileName = state.getArchiveFileName();
    myMainClass = state.getMainClass();
    myClasspath = state.getClasspath();
  }

  @Attribute("name")
  public String getArchiveFileName() {
    return myArchiveFileName;
  }

  public void setArchiveFileName(String archiveFileName) {
    myArchiveFileName = archiveFileName;
  }

  @Tag("main-class")
  public String getMainClass() {
    return myMainClass;
  }

  public void setMainClass(String mainClass) {
    myMainClass = mainClass;
  }

  @Tag("classpath")
  public String getClasspath() {
    return myClasspath;
  }

  public void setClasspath(String classpath) {
    myClasspath = classpath;
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
}
