import com.squareup.kotlinpoet.ClassName
import org.jetbrains.jewel.buildlogic.demodata.AndroidStudioReleasesGeneratorTask
import org.jetbrains.jewel.buildlogic.demodata.STUDIO_RELEASES_OUTPUT_CLASS_NAME
import org.jetbrains.jewel.buildlogic.demodata.StudioVersionsGenerationExtension

val extension: StudioVersionsGenerationExtension =
    extensions.findByType<StudioVersionsGenerationExtension>()
        ?: extensions.create("androidStudioReleasesGenerator", StudioVersionsGenerationExtension::class.java)

val task =
    tasks.register<AndroidStudioReleasesGeneratorTask>("generateAndroidStudioReleasesList") {
        val className = ClassName.bestGuess(STUDIO_RELEASES_OUTPUT_CLASS_NAME)
        val filePath = className.packageName.replace(".", "/") + "/${className.simpleName}.kt"
        outputFile = extension.targetDir.file(filePath)
        dataUrl = extension.dataUrl
        resourcesDirs = extension.resourcesDirs
    }
