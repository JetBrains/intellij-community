package com.intellij.compiler.ant.artifacts;

import com.intellij.compiler.ant.BuildProperties;
import com.intellij.compiler.ant.GenerationOptions;
import com.intellij.compiler.ant.Generator;
import com.intellij.compiler.ant.Comment;
import com.intellij.compiler.ant.taskdefs.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.elements.ArtifactPackagingElement;
import com.intellij.packaging.impl.elements.ModuleOutputPackagingElement;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class ArtifactsGenerator {
  @NonNls public static final String BUILD_ALL_ARTIFACTS_TARGET = "build.all.artifacts";
  @NonNls private static final String INIT_ARTIFACTS_TARGET = "init.artifacts";
  private final Project myProject;
  private final PackagingElementResolvingContext myResolvingContext;
  private ArtifactAntGenerationContextImpl myContext;

  public ArtifactsGenerator(Project project, GenerationOptions genOptions) {
    myProject = project;
    myResolvingContext = ArtifactManager.getInstance(myProject).getResolvingContext();
    myContext = new ArtifactAntGenerationContextImpl(project, genOptions);
  }

  public List<Generator> generate() {
    final List<Generator> generators = new ArrayList<Generator>();

    final Target initTarget = new Target(INIT_ARTIFACTS_TARGET, null, null, null);
    generators.add(initTarget);
    initTarget.add(new Property(ArtifactAntGenerationContextImpl.ARTIFACTS_TEMP_DIR_PROPERTY, BuildProperties.propertyRelativePath(BuildProperties.getProjectBaseDirProperty(), "artifactsTemp")));

    final Artifact[] artifacts = ArtifactManager.getInstance(myProject).getArtifacts();
    for (Artifact artifact : artifacts) {
      if (!myContext.shouldBuildIntoTempDirectory(artifact)) {
        generators.add(new CleanArtifactTarget(artifact, myContext));
      }
      final String outputPath = artifact.getOutputPath();
      if (!StringUtil.isEmpty(outputPath)) {
        initTarget.add(new Property(myContext.getConfiguredArtifactOutputProperty(artifact), myContext.getSubstitutedPath(outputPath)));
      }
    }

    StringBuilder depends = new StringBuilder();
    for (Artifact artifact : artifacts) {
      Target target = createArtifactTarget(artifact);
      generators.add(target);

      if (!StringUtil.isEmpty(artifact.getOutputPath())) {
        if (depends.length() > 0) depends.append(", ");
        depends.append(myContext.getTargetName(artifact));
      }
    }

    for (Generator generator : myContext.getBeforeBuildGenerators()) {
      initTarget.add(generator);
    }

    Target buildAllArtifacts = new Target(BUILD_ALL_ARTIFACTS_TARGET, depends.toString(), "Build all artifacts", null);
    for (Artifact artifact : artifacts) {
      final String artifactOutputPath = artifact.getOutputPath();
      if (!StringUtil.isEmpty(artifactOutputPath) && myContext.shouldBuildIntoTempDirectory(artifact)) {
        final String outputPath = BuildProperties.propertyRef(myContext.getConfiguredArtifactOutputProperty(artifact));
        buildAllArtifacts.add(new Mkdir(outputPath));
        final Copy copy = new Copy(outputPath);
        copy.add(new FileSet(BuildProperties.propertyRef(myContext.getArtifactOutputProperty(artifact))));
        buildAllArtifacts.add(copy);
      }
    }

    buildAllArtifacts.add(new Comment("Delete temporary files"), 1);
    for (Generator generator : myContext.getAfterBuildGenerators()) {
      buildAllArtifacts.add(generator);
    }
    buildAllArtifacts.add(new Delete(BuildProperties.propertyRef(ArtifactAntGenerationContextImpl.ARTIFACTS_TEMP_DIR_PROPERTY)));

    generators.add(buildAllArtifacts);
    return generators;
  }

  private Target createArtifactTarget(Artifact artifact) {
    final StringBuilder depends = new StringBuilder(INIT_ARTIFACTS_TARGET);

    //todo[nik] do not process content of included artifacts
    ArtifactUtil.processPackagingElements(artifact, null, new Processor<PackagingElement<?>>() {
      public boolean process(PackagingElement<?> packagingElement) {
        if (packagingElement instanceof ArtifactPackagingElement) {
          final Artifact included = ((ArtifactPackagingElement)packagingElement).findArtifact(myResolvingContext);
          if (included != null) {
            if (depends.length() > 0) depends.append(", ");
            depends.append(myContext.getTargetName(included));
          }
        }
        else if (packagingElement instanceof ModuleOutputPackagingElement) {
          final Module module = ((ModuleOutputPackagingElement)packagingElement).findModule(myResolvingContext);
          if (module != null) {
            if (depends.length() > 0) depends.append(", ");
            depends.append(BuildProperties.getCompileTargetName(module.getName()));
          }
        }
        return true;
      }
    }, myResolvingContext, true);

    final Target artifactTarget =
        new Target(myContext.getTargetName(artifact), depends.toString(), "Build '" + artifact.getName() + "' artifact", null);

    if (myContext.shouldBuildIntoTempDirectory(artifact)) {
      final String outputDirectory = BuildProperties.propertyRelativePath(ArtifactAntGenerationContextImpl.ARTIFACTS_TEMP_DIR_PROPERTY,
                                                                          BuildProperties.convertName(artifact.getName()));
      artifactTarget.add(new Property(myContext.getArtifactOutputProperty(artifact), outputDirectory));
    }

    final String outputPath = BuildProperties.propertyRef(myContext.getArtifactOutputProperty(artifact));
    artifactTarget.add(new Mkdir(outputPath));

    final DirectoryAntCopyInstructionCreator creator = new DirectoryAntCopyInstructionCreator(outputPath);

    List<Generator> copyInstructions = new ArrayList<Generator>();
    copyInstructions.addAll(artifact.getRootElement().computeAntInstructions(myResolvingContext, creator, myContext, artifact.getArtifactType()));

    for (Generator generator : myContext.getAndClearBeforeCurrentArtifact()) {
      artifactTarget.add(generator);
    }
    for (Generator tag : copyInstructions) {
      artifactTarget.add(tag);
    }
    return artifactTarget;
  }

  public List<String> getCleanTargetNames() {
    final List<String> targets = new ArrayList<String>();
    for (Artifact artifact : ArtifactManager.getInstance(myProject).getArtifacts()) {
      if (!myContext.shouldBuildIntoTempDirectory(artifact)) {
        targets.add(myContext.getCleanTargetName(artifact));
      }
    }
    return targets;
  }
}
