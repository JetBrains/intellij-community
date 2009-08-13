package com.intellij.packaging.impl.compiler;

import com.intellij.compiler.impl.ModuleCompileScope;
import com.intellij.compiler.impl.ProjectCompileScope;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.elements.ModuleOutputElementType;
import com.intellij.packaging.impl.elements.ModuleOutputPackagingElement;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.Collection;

/**
 * @author nik
 */
public class ArtifactCompileScope {
  private static final Key<Artifact[]> ARTIFACTS_KEY = Key.create("artifacts");

  private ArtifactCompileScope() {
  }

  @NotNull
  public static ModuleCompileScope create(@NotNull Project project, @NotNull Artifact artifact) {
    final Set<Module> modules = new HashSet<Module>();
    final PackagingElementResolvingContext context = ArtifactManager.getInstance(project).getResolvingContext();
    ArtifactUtil.processPackagingElements(artifact, ModuleOutputElementType.MODULE_OUTPUT_ELEMENT_TYPE, new Processor<ModuleOutputPackagingElement>() {
      public boolean process(ModuleOutputPackagingElement moduleOutputPackagingElement) {
        final Module module = moduleOutputPackagingElement.findModule(context);
        if (module != null) {
          modules.add(module);
        }
        return true;
      }
    }, context, true);

    final ModuleCompileScope scope = new ModuleCompileScope(project, modules.toArray(new Module[modules.size()]), true);
    scope.putUserData(ARTIFACTS_KEY, new Artifact[]{artifact});
    return scope;
  }

  public static ProjectCompileScope create(@NotNull Project project, @NotNull Collection<Artifact> artifacts) {
    final ProjectCompileScope scope = new ProjectCompileScope(project);
    scope.putUserData(ARTIFACTS_KEY, artifacts.toArray(new Artifact[artifacts.size()]));
    return scope;
  }

  @Nullable
  public static Artifact[] getArtifacts(@NotNull CompileScope compileScope) {
    return compileScope.getUserData(ARTIFACTS_KEY);
  }
}
