import com.squareup.kotlinpoet.ClassName
import org.jetbrains.jewel.buildlogic.demodata.AndroidStudioReleasesGeneratorTask
import org.jetbrains.jewel.buildlogic.demodata.STUDIO_RELEASES_OUTPUT_CLASS_NAME
import org.jetbrains.jewel.buildlogic.demodata.StudioVersionsGenerationExtension

val extension: StudioVersionsGenerationExtension =
    extensions.findByType<StudioVersionsGenerationExtension>()
        ?: extensions.create("androidStudioReleasesGenerator", StudioVersionsGenerationExtension::class.java)

val task =
    tasks.register<AndroidStudioReleasesGeneratorTask>("generateAndroidStudioReleasesList") {
        // Default output path mirrors the package of the generated class; override `outputFile`
        // directly when the target source tree doesn't mirror its package structure.
        outputFile =
            extension.targetDir.file(
                extension.outputClassName.map {
                    val className = ClassName.bestGuess(it)
                    className.packageName.replace(".", "/") + "/${className.simpleName}.kt"
                }
            )
        dataUrl = extension.dataUrl
        resourcesDirs = extension.resourcesDirs
        outputClassName = extension.outputClassName
        modelPackage = extension.modelPackage
        indent = extension.indent
    }
