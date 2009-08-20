package com.intellij.packaging.impl.elements;

import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactUtil;
import com.intellij.openapi.roots.ui.configuration.artifacts.ChooseArtifactsDialog;
import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactPointerManager;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.packaging.impl.artifacts.PlainArtifactType;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

/**
* @author nik
*/
public class ArtifactElementType extends PackagingElementType<ArtifactPackagingElement> {
  public static final ArtifactElementType ARTIFACT_ELEMENT_TYPE = new ArtifactElementType();

  ArtifactElementType() {
    super("artifact", CompilerBundle.message("element.type.name.artifact"));
  }

  @Override
  public Icon getCreateElementIcon() {
    return PlainArtifactType.ARTIFACT_ICON;
  }

  @Override
  public boolean canCreate(@NotNull PackagingEditorContext context, @NotNull Artifact artifact) {
    return !getAvailableArtifacts(context, artifact).isEmpty();
  }

  @NotNull
  public List<? extends ArtifactPackagingElement> chooseAndCreate(@NotNull PackagingEditorContext context, @NotNull Artifact artifact,
                                                                   @NotNull CompositePackagingElement<?> parent) {
    final Project project = context.getProject();
    ChooseArtifactsDialog dialog = new ChooseArtifactsDialog(project, getAvailableArtifacts(context, artifact),
                                                             CompilerBundle.message("dialog.title.choose.artifacts"), "");
    dialog.show();
    final List<ArtifactPackagingElement> elements = new ArrayList<ArtifactPackagingElement>();
    if (dialog.isOK()) {
      for (Artifact selected : dialog.getChosenElements()) {
        elements.add(new ArtifactPackagingElement(project, ArtifactPointerManager.getInstance(project).create(selected.getName())));
      }
    }
    return elements;
  }

  @NotNull
  public static List<? extends Artifact> getAvailableArtifacts(@NotNull final PackagingEditorContext context, @NotNull final Artifact artifact) {
    final Set<Artifact> result = new HashSet<Artifact>(Arrays.asList(context.getArtifactModel().getArtifacts()));
    ArtifactUtil.processPackagingElements(artifact, ARTIFACT_ELEMENT_TYPE, new Processor<ArtifactPackagingElement>() {
      public boolean process(ArtifactPackagingElement artifactPackagingElement) {
        result.remove(artifactPackagingElement.findArtifact(context));
        return true;
      }
    }, context, true);
    result.remove(artifact);
    final Iterator<Artifact> iterator = result.iterator();
    while (iterator.hasNext()) {
      Artifact another = iterator.next();
      final boolean notContainThis =
          ArtifactUtil.processPackagingElements(another, ARTIFACT_ELEMENT_TYPE, new Processor<ArtifactPackagingElement>() {
            public boolean process(ArtifactPackagingElement element) {
              return !artifact.getName().equals(element.getArtifactName());
            }
          }, context, true);
      if (!notContainThis) {
        iterator.remove();
      }
    }
    return new ArrayList<Artifact>(result);
  }

  @NotNull
  public ArtifactPackagingElement createEmpty(@NotNull Project project) {
    return new ArtifactPackagingElement(project);
  }
}
