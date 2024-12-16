/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.jetbrains.bazel.jvm.kotlin

import java.io.File
import java.io.PrintWriter
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern
import kotlin.io.path.ExperimentalPathApi

private val FLAG_FILE_RE = Pattern.compile("""^--flagfile=((.*)-(\d+).params)$""").toRegex()

internal fun buildKotlin(
  workingDir: Path,
  out: Writer,
  args: List<String>,
  sources: List<File>,
): Int {
  check(args.isNotEmpty()) {
    "expected at least a single arg got: ${args.joinToString(" ")}"
  }

  val argMap = createArgMap(
    FLAG_FILE_RE.matchEntire(args[0])?.groups?.get(1)?.let {
      Files.readAllLines(Path.of(it.value))
    } ?: args,
    enumClass = KotlinBuilderFlags::class.java,
  )
  val task = createBuildTask(argMap)
  val compileContext = CompilationTaskContext(
    label = task.label,
    debug = argMap.optional(KotlinBuilderFlags.KOTLIN_DEBUG_TAGS) ?: emptyList(),
    out = out
  )
  var success = false
  try {
    when (task.platform) {
      Platform.JVM -> {
        val task = createJvmTask(info = task, workingDir = workingDir, args = argMap)
        if (compileContext.isTracing) {
          compileContext.out.appendLine(formatDataClassToString(task.toString()))
        }
        compileContext.execute("compile classes") {
          val code = compileContext.execute("kotlinc") {
            doCompileKotlin(task = task, context = compileContext, sources = sources)
          }
          if (code != 0) {
            return code
          }

          //packOutput(task, compileContext)
        }
      }

      Platform.UNRECOGNIZED -> throw IllegalStateException("unrecognized platform: $task")
    }
    success = true
  }
  catch (e: CompilationStatusException) {
    out.appendLine("Compilation failure: ${e.message}")
    return e.status
  }
  catch (e: Throwable) {
    out.appendLine(e.message ?: "unknown error")
    PrintWriter(out).use { e.printStackTrace(it) }
  }
  finally {
    compileContext.finalize(success)
  }
  return 0
}

private fun formatDataClassToString(input: String): CharSequence {
  val indentUnit = "  "
  var currentIndent = ""
  val result = StringBuilder()

  for (char in input) {
    when (char) {
      '{', '[', '(' -> {
        result.append("$char\n")
        currentIndent += indentUnit
        result.append(currentIndent)
      }

      '}', ']', ')' -> {
        result.append("\n")
        currentIndent = currentIndent.dropLast(indentUnit.length)
        result.append(currentIndent).append(char)
      }

      ',' -> result.append(",\n").append(currentIndent)
      else -> result.append(char)
    }
  }

  return result
}

private fun createBuildTask(argMap: ArgMap<KotlinBuilderFlags>): CompilationTaskInfo {
  val ruleKind = argMap.mandatorySingle(KotlinBuilderFlags.RULE_KIND).split('_')
  check(ruleKind.size == 3 && ruleKind[0] == "kt") {
    "invalid rule kind $ruleKind"
  }

  return CompilationTaskInfo(
    label = argMap.mandatorySingle(KotlinBuilderFlags.TARGET_LABEL),
    ruleKind = checkNotNull(RuleKind.valueOf(ruleKind[2].uppercase())) {
      "unrecognized rule kind ${ruleKind[2]}"
    },
    platform = checkNotNull(Platform.valueOf(ruleKind[1].uppercase())) {
      "unrecognized platform ${ruleKind[1]}"
    },
    moduleName = argMap.mandatorySingle(KotlinBuilderFlags.KOTLIN_MODULE_NAME).also {
      check(it.isNotBlank()) { "--kotlin_module_name should not be blank" }
    },
  )
}

@OptIn(ExperimentalPathApi::class)
private fun createJvmTask(
  info: CompilationTaskInfo,
  workingDir: Path,
  args: ArgMap<KotlinBuilderFlags>,
): JvmCompilationTask {
  var outJarPath = args.optionalSingle(KotlinBuilderFlags.OUTPUT)
  val generatedKspSrcJar = args.optionalSingle(KotlinBuilderFlags.KSP_GENERATED_JAVA_SRCJAR)
  if (outJarPath == null) {
    outJarPath = generatedKspSrcJar!!
  }
  val outJar = workingDir.resolve(outJarPath)

  //val kotlincDir = jar.parent.resolve("_kotlinc")
  //Files.createDirectories(kotlincDir)
  val targetName = outJar.fileName.toString().substringBeforeLast(".jar")

  //fun resolveAndCreate(part: String): Path {
  //  val dir = kotlincDir.resolve("$targetName-$part")
  //  dir.deleteRecursively()
  //  Files.createDirectory(dir)
  //  return dir
  //}

  //fun resolve(part: String): Path {
  //  return kotlincDir.resolve("$targetName-$part")
  //}

  //val tempDir = resolve("temp")

  fun pathList(list: List<String>): List<Path> {
    return list.map { workingDir.resolve(it).toAbsolutePath().normalize() }
  }

  val root = JvmCompilationTask(
    workingDir = workingDir,
    args = args,
    info = info,
    outJar = outJar,
    outputs = Outputs(
      srcjar = args.optionalSingle(KotlinBuilderFlags.KOTLIN_OUTPUT_SRCJAR)?.let { workingDir.resolve(it) },
      jdeps = args.optionalSingle(KotlinBuilderFlags.KOTLIN_OUTPUT_JDEPS)?.let { workingDir.resolve(it) },
      abiJar = args.optionalSingle(KotlinBuilderFlags.ABI_JAR)?.let { workingDir.resolve(it) },
    ),
    //directories = Directories(
    //  //classes = resolveAndCreate("classes"),
    //  //abiClasses = if (args.has(KotlinBuilderFlags.ABI_JAR)) resolveAndCreate("abi-classes") else null,
    //
    //  //temp = tempDir,
    //  //incrementalData = resolve("incremental"),
    //),
    inputs = Inputs(
      classpath = pathList(args.mandatory(KotlinBuilderFlags.CLASSPATH)),
      depsArtifacts = args.optional(KotlinBuilderFlags.DEPS_ARTIFACTS) ?: emptyList(),
      directDependencies = args.mandatory(KotlinBuilderFlags.DIRECT_DEPENDENCIES),
      processors = args.optional(KotlinBuilderFlags.PROCESSORS) ?: emptyList(),
      processorPaths = args.optional(KotlinBuilderFlags.PROCESSOR_PATH) ?: emptyList(),
      stubsPluginOptions = args.optional(KotlinBuilderFlags.STUBS_PLUGIN_OPTIONS) ?: emptyList(),
      stubsPluginClasspath = args.optional(KotlinBuilderFlags.STUBS_PLUGIN_CLASSPATH) ?: emptyList(),
      compilerPluginClasspath = args.optional(KotlinBuilderFlags.COMPILER_PLUGIN_CLASSPATH)?.let { pathList(it) } ?: emptyList(),
    ),
  )
  return root
}

