package com.intellij.compiler.ant.artifacts;

import com.intellij.compiler.ant.BuildProperties;
import com.intellij.compiler.ant.GenerationOptions;
import com.intellij.compiler.ant.Generator;
import com.intellij.compiler.ant.Comment;
import com.intellij.compiler.ant.taskdefs.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactUtil;
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
  private ArtifactGenerationContextImpl myContext;

  public ArtifactsGenerator(Project project, GenerationOptions genOptions) {
    myProject = project;
    myResolvingContext = ArtifactManager.getInstance(myProject).getResolvingContext();
    myContext = new ArtifactGenerationContextImpl(project, genOptions);
  }

  public List<Generator> generate() {
    final List<Generator> generators = new ArrayList<Generator>();

    final Target initTarget = new Target(INIT_ARTIFACTS_TARGET, null, null, null);
    generators.add(initTarget);
    initTarget.add(new Property(ArtifactGenerationContextImpl.ARTIFACTS_TEMP_DIR_PROPERTY, BuildProperties.propertyRelativePath(BuildProperties.getProjectBaseDirProperty(), "artifactsTemp")));

    StringBuilder depends = new StringBuilder();
    final Artifact[] artifacts = ArtifactManager.getInstance(myProject).getArtifacts();
    for (Artifact artifact : artifacts) {
      Target target = createArtifactTarget(artifact);
      generators.add(target);

      if (artifact.isBuildOnMake()) {
        if (depends.length() > 0) depends.append(", ");
        depends.append(myContext.getTargetName(artifact));
      }
    }

    for (Generator generator : myContext.getBeforeBuildGenerators()) {
      initTarget.add(generator);
    }

    Target buildAllArtifacts = new Target(BUILD_ALL_ARTIFACTS_TARGET, depends.toString(), "Build all artifacts", null);
    for (Artifact artifact : artifacts) {
      if (artifact.isBuildOnMake()) {
        final String outputPath = myContext.getSubstitutedPath(artifact.getOutputPath());
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
    buildAllArtifacts.add(new Delete(BuildProperties.propertyRef(ArtifactGenerationContextImpl.ARTIFACTS_TEMP_DIR_PROPERTY)));

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
    final String tempOutputDirectory = BuildProperties.propertyRelativePath(ArtifactGenerationContextImpl.ARTIFACTS_TEMP_DIR_PROPERTY,
                                                                            BuildProperties.convertName(artifact.getName()));
    artifactTarget.add(new Property(myContext.getArtifactOutputProperty(artifact), tempOutputDirectory));

    final String outputPath = BuildProperties.propertyRef(myContext.getArtifactOutputProperty(artifact));
    artifactTarget.add(new Mkdir(outputPath));

    final DirectoryCopyInstructionCreator creator = new DirectoryCopyInstructionCreator(outputPath);

    List<Generator> copyInstructions = new ArrayList<Generator>();
    for (PackagingElement<?> element : artifact.getRootElement().getChildren()) {
      copyInstructions.addAll(element.computeCopyInstructions(myResolvingContext, creator, myContext));
    }

    for (Generator generator : myContext.getAndClearBeforeCurrentArtifact()) {
      artifactTarget.add(generator);
    }
    for (Generator tag : copyInstructions) {
      artifactTarget.add(tag);
    }
    return artifactTarget;
  }

}
