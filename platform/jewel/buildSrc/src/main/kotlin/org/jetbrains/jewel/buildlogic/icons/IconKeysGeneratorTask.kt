package org.jetbrains.jewel.buildlogic.icons

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.property
import java.lang.Deprecated
import java.lang.reflect.Field
import java.net.URLClassLoader
import javax.swing.Icon
import kotlin.Boolean
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.apply
import kotlin.let

open class IconKeysGeneratorTask : DefaultTask() {
    @get:OutputDirectory val outputDirectory: DirectoryProperty = project.objects.directoryProperty()

    @get:Input val sourceClassName = project.objects.property<String>()

    @get:Input val generatedClassName = project.objects.property<String>()

    @get:InputFiles val configuration: ConfigurableFileCollection = project.objects.fileCollection()

    init {
        group = "jewel"
    }

    @TaskAction
    fun generate() {
        val guessedSourceClassName =
            sourceClassName.map { ClassName.bestGuess(it).canonicalName.replace('.', '/') + ".kt" }.get()

        // The icons artifacts are loaded on the iconGeneration configuration's classpath,
        // so we need a classloader that can access these classes.
        val classLoader = createClassLoader()
        val sourceClass =
            classLoader.loadClass(sourceClassName.get())
                ?: throw GradleException(
                    "Unable to load ${sourceClassName.get()}. " +
                        "Is the correct dependency declared on the iconGeneration configuration?"
                )

        // Traverse sourceClass by using reflection, collecting all members
        // This step uses the mappings to add the new paths where available.
        val dummyIconClass = classLoader.loadClass("com.intellij.ui.DummyIconImpl")
        val oldUiPathField = dummyIconClass.getOriginalPathField()
        val newUiPathField = dummyIconClass.getNewUiPathField()

        val rootHolder = IconKeyHolder(sourceClass.simpleName)
        whileForcingAccessible(oldUiPathField, newUiPathField) {
            visitSourceClass(sourceClass, rootHolder, oldUiPathField, newUiPathField, classLoader)
        }
        logger.lifecycle("Read icon keys from ${sourceClass.name}")

        // Step 4) Generate output Kotlin file
        val fileSpec = generateKotlinCode(rootHolder)
        val directory = outputDirectory.get().asFile
        fileSpec.writeTo(directory)

        logger.lifecycle("Written icon keys for $guessedSourceClassName into $directory")
    }

    private fun createClassLoader(): URLClassLoader {
        val arrayOfURLs = configuration.files.map { it.toURI().toURL() }.toTypedArray()

        return URLClassLoader(arrayOfURLs, IconKeysGeneratorTask::class.java.classLoader)
    }

    private fun whileForcingAccessible(vararg fields: Field, action: () -> Unit) {
        val wasAccessibles = mutableListOf<Boolean>()
        for (field in fields) {
            @Suppress("DEPRECATION")
            wasAccessibles += field.isAccessible
            field.isAccessible = true
        }

        try {
            action()
        } finally {
            for ((index, field) in fields.withIndex()) {
                field.isAccessible = wasAccessibles[index]
            }
        }
    }

    private fun visitSourceClass(
        sourceClass: Class<*>,
        parentHolder: IconKeyHolder,
        oldUiPathField: Field,
        newUiPathField: Field,
        classLoader: ClassLoader,
    ) {
        for (child in sourceClass.declaredClasses) {
            val childName = "${parentHolder.name}.${child.simpleName}"
            val childHolder = IconKeyHolder(childName)
            parentHolder.holders += childHolder
            visitSourceClass(child, childHolder, oldUiPathField, newUiPathField, classLoader)
        }
        parentHolder.holders.sortBy { it.name }

        sourceClass.declaredFields
            .filter { it.type == Icon::class.java }
            .forEach { field ->
                val fieldName = "${parentHolder.name}.${field.name}"

                if (field.annotations.any { it.annotationClass == Deprecated::class }) {
                    logger.lifecycle("Ignoring deprecated field: $fieldName")
                    return@forEach
                }

                val icon = field.get(sourceClass)
                val oldUiPath =
                    oldUiPathField.get(icon) as String? ?: throw GradleException("Found null path in icon $fieldName")
                validatePath(oldUiPath, fieldName, classLoader)

                // New UI paths may be "partial", meaning they end with a / character.
                // In this case, we're supposed to append the old UI path to the new UI
                // path, because that's just how they decided to encode things in IJP.
                val newUiPath =
                    (newUiPathField.get(icon) as String?)?.let { if (it.endsWith("/")) it + oldUiPath else it }
                        ?: oldUiPath
                validatePath(newUiPath, fieldName, classLoader)
                parentHolder.keys += IconKey(fieldName, oldUiPath, newUiPath)
            }
        parentHolder.keys.sortBy { it.name }
    }

    private fun validatePath(path: String, fieldName: String, classLoader: ClassLoader) {
        val iconsClass = classLoader.loadClass(sourceClassName.get())
        if (iconsClass.getResourceAsStream("/${path.trimStart('/')}") == null) {
            logger.warn("Icon $fieldName: '$path' does not exist")
        }
    }

    private fun generateKotlinCode(rootHolder: IconKeyHolder): FileSpec {
        val className = ClassName.bestGuess(generatedClassName.get())

        return FileSpec.builder(className)
            .apply {
                indent("    ")
                addFileComment("Generated by the Jewel icon keys generator\n")
                addFileComment("Source class: ${sourceClassName.get()}")

                addImport(keyClassName.packageName, keyClassName.simpleName)
                addImport(
                    "org.jetbrains.jewel.foundation",
                    "GeneratedFromIntelliJSources",
                )

                val objectName = ClassName.bestGuess(generatedClassName.get())
                addType(
                    TypeSpec.objectBuilder(objectName)
                        .apply {
                            addAnnotation(
                                AnnotationSpec.builder(
                                        ClassName.bestGuess(
                                            "org.jetbrains.jewel.foundation.GeneratedFromIntelliJSources"
                                        )
                                    )
                                    .build()
                            )

                            for (childHolder in rootHolder.holders) {
                                generateKotlinCodeInner(childHolder, className)
                            }

                            for (key in rootHolder.keys) {
                                addProperty(buildIconKeyEntry(key, className))
                            }
                        }
                        .build()
                )
            }
            .build()
    }

    private fun TypeSpec.Builder.generateKotlinCodeInner(holder: IconKeyHolder, rootClassName: ClassName) {
        val objectName = holder.name.substringAfterLast('.')
        addType(
            TypeSpec.objectBuilder(objectName)
                .apply {
                    addAnnotation(
                        AnnotationSpec.builder(
                                ClassName.bestGuess("org.jetbrains.jewel.foundation.GeneratedFromIntelliJSources")
                            )
                            .build()
                    )

                    for (childHolder in holder.holders) {
                        generateKotlinCodeInner(childHolder, rootClassName)
                    }

                    for (key in holder.keys) {
                        addProperty(buildIconKeyEntry(key, rootClassName))
                    }
                }
                .build()
        )
    }

    private fun buildIconKeyEntry(key: IconKey, rootClassName: ClassName) =
        PropertySpec.builder(key.name.substringAfterLast('.'), keyClassName)
            .initializer(
                "%L",
                "IntelliJIconKey(\"${key.oldPath}\", " +
                    "\"${key.newPath ?: key.oldPath}\", " +
                    "${rootClassName.simpleName}::class.java)",
            )
            .addAnnotation(
                AnnotationSpec.builder(
                        ClassName.bestGuess("org.jetbrains.jewel.foundation.GeneratedFromIntelliJSources")
                    )
                    .build()
            )
            .build()

    private data class IconKeyHolder(
        val name: String,
        val holders: MutableList<IconKeyHolder> = mutableListOf(),
        val keys: MutableList<IconKey> = mutableListOf(),
    )

    private data class IconKey(val name: String, val oldPath: String, val newPath: String?)

    companion object {
        private fun Class<*>.getOriginalPathField(): Field = declaredFields.first { it.name == "originalPath" }

        private fun Class<*>.getNewUiPathField(): Field = declaredFields.first { it.name == "expUIPath" }

        private val keyClassName = ClassName("org.jetbrains.jewel.ui.icon", "IntelliJIconKey")
    }
}
