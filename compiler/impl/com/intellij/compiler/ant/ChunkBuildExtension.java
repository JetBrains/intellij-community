package com.intellij.compiler.ant;

import com.intellij.ExtensionPoints;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

public abstract class ChunkBuildExtension {

  public abstract boolean haveSelfOutputs(Module[] modules);

  @Nullable
  public abstract String getAssemblingName(final Module[] modules, final String name);

  public abstract void process(Project project, ModuleChunk chunk, GenerationOptions genOptions, CompositeGenerator generator);

  public static boolean hasSelfOutput(ModuleChunk chunk) {
    final Object[] objects = Extensions.getRootArea()
      .getExtensionPoint(ExtensionPoints.ANT_BUILD_GEN).getExtensions();
    final Module[] modules = chunk.getModules();
    for (Object object : objects) {
      if (!((ChunkBuildExtension)object).haveSelfOutputs(modules)) return false;
    }
    return true;
  }

  public static String getAssemblingName(ModuleChunk chunk) {
    final Module[] modules = chunk.getModules();
    final String chunkName = chunk.getName();
    final Object[] objects = Extensions.getRootArea()
      .getExtensionPoint(ExtensionPoints.ANT_BUILD_GEN).getExtensions();
    for (Object object : objects) {
      final String name = ((ChunkBuildExtension)object).getAssemblingName(modules, chunkName);
      if (name != null) {
        return name;
      }
    }
    return BuildProperties.getCompileTargetName(chunkName);
  }

  public static void process(CompositeGenerator generator, ModuleChunk chunk, GenerationOptions genOptions) {
    final Project project = chunk.getProject();
    final Object[] objects = Extensions.getRootArea()
      .getExtensionPoint(ExtensionPoints.ANT_BUILD_GEN).getExtensions();
    for (Object object : objects) {
      ((ChunkBuildExtension)object).process(project, chunk, genOptions, generator);
    }
  }

}