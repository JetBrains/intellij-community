// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.DefaultTask
import org.gradle.util.GFileUtils
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact

class OfflineMavenRepository extends DefaultTask {
  @Input
  String configurationName = 'compile'

  @OutputDirectory
  File repoDir = new File(project.buildDir, 'offline-repo')

  @TaskAction
  void build() {
    Configuration configuration = project.configurations.getByName(configurationName)
    copyJars(configuration)
    copyPoms(configuration)
  }

  private void copyJars(Configuration configuration) {
    configuration.resolvedConfiguration.resolvedArtifacts.each { artifact ->
      def moduleVersionId = artifact.moduleVersion.id
      File moduleDir = new File(repoDir, "${moduleVersionId.group.replace('.','/')}/${moduleVersionId.name}/${moduleVersionId.version}")
      GFileUtils.mkdirs(moduleDir)

      File target = new File(moduleDir, artifact.file.name)
      println "Copy: $artifact.file -> $target"
      GFileUtils.copyFile(artifact.file, target)
    }
  }

  private void copyPoms(Configuration configuration) {
    def componentIds = configuration.incoming.resolutionResult.allDependencies.collect { it.selected.id }

    def result = project.dependencies.createArtifactResolutionQuery()
      .forComponents(componentIds)
      .withArtifacts(MavenModule, MavenPomArtifact)
      .execute()

    for(component in result.resolvedComponents) {
      def componentId = component.id

      if(componentId instanceof ModuleComponentIdentifier) {
        File moduleDir = new File(repoDir, "${componentId.group.replace('.','/')}/${componentId.module}/${componentId.version}")
        GFileUtils.mkdirs(moduleDir)
        File pomFile = component.getArtifacts(MavenPomArtifact)[0].file
        GFileUtils.copyFile(pomFile, new File(moduleDir, pomFile.name))
      }
    }
  }
}