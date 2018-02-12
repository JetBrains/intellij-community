/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.builders.java

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.MultiMap
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.directoryContent
import com.intellij.util.io.java.AccessModifier
import com.intellij.util.io.java.ClassFileBuilder
import com.intellij.util.io.java.classFile
import gnu.trove.THashSet
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.DirtyFilesHolder
import org.jetbrains.jps.builders.storage.StorageProvider
import org.jetbrains.jps.incremental.BuilderCategory
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.ModuleLevelBuilder
import org.jetbrains.jps.incremental.storage.AbstractStateStorage
import org.jetbrains.jps.incremental.storage.PathStringDescriptor
import org.jetbrains.jps.model.java.LanguageLevel
import org.jetbrains.org.objectweb.asm.ClassReader
import java.io.File
import java.util.*
import java.util.regex.Pattern

/**
 * Mock builder which produces class file from several source files to test that our build infrastructure handle such cases properly.
 *
 * The builder processes *.p file, generates empty class for each such file and generates 'PackageFacade' class for each package
 * which references all classes from that package. Package name is derived from 'package <name>;' statement from a file or set to empty
 * if no such statement is found
 *
 * @author nik
 */
class MockPackageFacadeGenerator : ModuleLevelBuilder(BuilderCategory.SOURCE_PROCESSOR) {
  override fun build(context: CompileContext,
                     chunk: ModuleChunk,
                     dirtyFilesHolder: DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget>,
                     outputConsumer: ModuleLevelBuilder.OutputConsumer): ModuleLevelBuilder.ExitCode {
    val filesToCompile = MultiMap.createLinked<ModuleBuildTarget, File>()
    dirtyFilesHolder.processDirtyFiles { target, file, root ->
      if (isCompilable(file)) {
        filesToCompile.putValue(target, file)
      }
      true
    }

    val allFilesToCompile = ArrayList(filesToCompile.values())
    if (allFilesToCompile.isEmpty() && chunk.targets.all { dirtyFilesHolder.getRemovedFiles(it).all { !isCompilable(File(it)) } }) return ModuleLevelBuilder.ExitCode.NOTHING_DONE

    if (JavaBuilderUtil.isCompileJavaIncrementally(context)) {
      val logger = context.loggingManager.projectBuilderLogger
      if (logger.isEnabled) {
        if (!filesToCompile.isEmpty) {
          logger.logCompiledFiles(allFilesToCompile, "MockPackageFacadeGenerator", "Compiling files:")
        }
      }
    }

    val mappings = context.projectDescriptor.dataManager.mappings
    val callback = JavaBuilderUtil.getDependenciesRegistrar(context)

    fun generateClass(packageName: String, className: String, target: ModuleBuildTarget, sources: Collection<String>,
                      allSources: Collection<String>, content: (ClassFileBuilder.() -> Unit)? = null) {
      val fullClassName = StringUtil.getQualifiedName(packageName, className)
      directoryContent {
        classFile(fullClassName) {
          javaVersion = LanguageLevel.JDK_1_6
          if (content != null) {
            content()
          }
        }
      }.generate(target.outputDir!!)
      val outputFile = File(target.outputDir, "${fullClassName.replace('.', '/')}.class")
      outputConsumer.registerOutputFile(target, outputFile, sources)
      callback.associate(fullClassName, allSources, ClassReader(outputFile.readBytes()))
    }

    for (target in chunk.targets) {
      val packagesStorage = context.projectDescriptor.dataManager.getStorage(target, PACKAGE_CACHE_STORAGE_PROVIDER)
      for (file in filesToCompile[target]) {
        val sources = listOf(file.absolutePath)
        generateClass(getPackageName(file), FileUtil.getNameWithoutExtension(file), target, sources, sources)
      }

      val packagesToGenerate = LinkedHashMap<String, MutableList<File>>()
      filesToCompile[target].forEach {
        val currentName = getPackageName(it)
        if (currentName !in packagesToGenerate) packagesToGenerate[currentName] = ArrayList()
        packagesToGenerate[currentName]!!.add(it)
        val oldName = packagesStorage.getState(it.absolutePath)
        if (oldName != null && oldName != currentName && oldName !in packagesToGenerate) {
          packagesToGenerate[oldName] = ArrayList()
        }
      }
      val packagesFromDeletedFiles = dirtyFilesHolder.getRemovedFiles(target).filter { isCompilable(File(it)) }.map { packagesStorage.getState(it) }.filterNotNull()
      packagesFromDeletedFiles.forEach {
        if (it !in packagesToGenerate) {
          packagesToGenerate[it] = ArrayList()
        }
      }

      val getParentFile: (File) -> File = { it.parentFile }
      val dirsToCheck = filesToCompile[target].mapTo(THashSet(FileUtil.FILE_HASHING_STRATEGY), getParentFile)
      packagesFromDeletedFiles.flatMap {
        mappings.getClassSources(mappings.getName(StringUtil.getQualifiedName(it, "PackageFacade"))) ?: emptyList()
      }.map(getParentFile).filterNotNullTo(dirsToCheck)

      for ((packageName, dirtyFiles) in packagesToGenerate) {
        val files = dirsToCheck.map { it.listFiles() }.filterNotNull().flatMap { it.toList() }.filter { isCompilable(it) && packageName == getPackageName(it) }
        if (files.isEmpty()) continue

        val classNames = files.map { FileUtilRt.getNameWithoutExtension(it.name) }.sorted()
        val dirtySource = dirtyFiles.map { it.absolutePath }
        val allSources = files.map { it.absolutePath }

        generateClass(packageName, "PackageFacade", target, dirtySource, allSources) {
          for (fileName in classNames) {
            val fieldClass = StringUtil.getQualifiedName(packageName, fileName)
            field(StringUtil.decapitalize(fileName), fieldClass, AccessModifier.PUBLIC)
          }
        }
        for (source in dirtySource) {
          packagesStorage.update(FileUtil.toSystemIndependentName(source), packageName)
        }
      }
    }
    JavaBuilderUtil.registerFilesToCompile(context, allFilesToCompile)
    JavaBuilderUtil.registerSuccessfullyCompiled(context, allFilesToCompile)
    return ModuleLevelBuilder.ExitCode.OK
  }

  override fun getCompilableFileExtensions(): List<String> {
    return listOf("p")
  }

  override fun getPresentableName(): String {
    return "Mock Package Facade Generator"
  }

  companion object {
    private val PACKAGE_CACHE_STORAGE_PROVIDER = object : StorageProvider<AbstractStateStorage<String, String>>() {
      override fun createStorage(targetDataDir: File): AbstractStateStorage<String, String> {
        val storageFile = File(targetDataDir, "mockPackageFacade/packages")
        return object : AbstractStateStorage<String, String>(storageFile, PathStringDescriptor(), EnumeratorStringDescriptor()) {
        }
      }
    }

    private fun getPackageName(sourceFile: File): String {
      val text = String(FileUtil.loadFileText(sourceFile))
      val matcher = Pattern.compile("\\p{javaWhitespace}*package\\p{javaWhitespace}+([^;]*);.*").matcher(text)
      if (matcher.matches()) {
        return matcher.group(1)
      }
      return ""
    }

    private fun isCompilable(file: File): Boolean {
      return FileUtilRt.extensionEquals(file.name, "p")
    }
  }
}