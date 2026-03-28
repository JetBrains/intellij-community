@file:Suppress("UnstableApiUsage")

import dev.detekt.gradle.Detekt
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.dokka.gradle.tasks.DokkaGenerateModuleTask
import org.jetbrains.dokka.gradle.tasks.DokkaGeneratePublicationTask
import org.jetbrains.jewel.buildlogic.icons.IconKeysGeneratorTask
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.BaseKotlinCompile

private val defaultOutputDir: Provider<Directory> = layout.buildDirectory.dir("generated/iconKeys")

class IconKeysGeneratorContainer(container: NamedDomainObjectContainer<IconKeysGeneration>) :
    NamedDomainObjectContainer<IconKeysGeneration> by container

class IconKeysGeneration(val name: String, project: Project) {
    val outputDirectory: DirectoryProperty = project.objects.directoryProperty().convention(defaultOutputDir)

    val sourceClassName: Property<String> = project.objects.property<String>()
    val generatedClassName: Property<String> = project.objects.property<String>()
}

val iconGeneration by
    configurations.registering {
        isCanBeConsumed = false
        isCanBeResolved = true
    }

val extension = IconKeysGeneratorContainer(container<IconKeysGeneration> { IconKeysGeneration(it, project) })

extensions.add("intelliJIconKeysGenerator", extension)

extension.all item@{
    val task =
        tasks.register<IconKeysGeneratorTask>("generate${name}Keys") task@{
            this@task.outputDirectory = this@item.outputDirectory
            this@task.sourceClassName = this@item.sourceClassName
            this@task.generatedClassName = this@item.generatedClassName
            configuration.from(iconGeneration)
            dependsOn(iconGeneration)
        }

    tasks {
        withType<BaseKotlinCompile> { dependsOn(task) }
        withType<Detekt> { dependsOn(task) }
        withType<DokkaTask> { dependsOn(task) }
        withType<DokkaGenerateModuleTask> { dependsOn(task) }
        withType<DokkaGeneratePublicationTask> { dependsOn(task) }
        withType<Jar> { dependsOn(task) }
    }
}

pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    the<KotlinJvmProjectExtension>().sourceSets["main"].kotlin.srcDir(defaultOutputDir)
}
