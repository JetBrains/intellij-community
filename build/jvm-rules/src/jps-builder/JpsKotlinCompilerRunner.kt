@file:Suppress("PackageDirectoryMismatch")

package org.jetbrains.kotlin.compilerRunner

import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2MetadataCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.mergeBeans
import org.jetbrains.kotlin.config.CompilerSettings
import org.jetbrains.kotlin.config.splitArgumentString
import org.jetbrains.kotlin.jps.statistic.JpsBuilderMetricReporter
import org.jetbrains.kotlin.preloading.ClassCondition
import org.jetbrains.kotlin.preloading.ClassPreloadingUtils
import org.jetbrains.kotlin.preloading.Preloader
import org.jetbrains.kotlin.utils.KotlinPaths
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.lang.IllegalStateException
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.Path

internal object DummyKotlinPaths : KotlinPaths {
  override val homePath: File
    get() = throw kotlin.IllegalStateException()

  override val libPath: File
    get() = throw kotlin.IllegalStateException()

  override fun jar(jar: KotlinPaths.Jar): File = throw kotlin.IllegalStateException()

  override fun klib(jar: KotlinPaths.Jar): File = throw kotlin.IllegalStateException()

  override fun sourcesJar(jar: KotlinPaths.Jar): File? = throw kotlin.IllegalStateException()
}

internal val classesToLoadByParent = ClassCondition { className ->
  for (it in arrayOf(
    "org.apache.log4j.", // For logging from compiler
    "org.jetbrains.kotlin.incremental.components.",
    "org.jetbrains.kotlin.incremental.js",
    "org.jetbrains.kotlin.load.kotlin.incremental.components."
  )) {
    if (className.startsWith(it)) {
      return@ClassCondition true
    }
  }
  for (it in arrayOf(
    "org.jetbrains.kotlin.config.Services",
    "org.jetbrains.kotlin.progress.CompilationCanceledStatus",
    "org.jetbrains.kotlin.progress.CompilationCanceledException",
    "org.jetbrains.kotlin.modules.TargetId",
    "org.jetbrains.kotlin.cli.common.ExitCode"
  )) {
    if (className == it) {
      return@ClassCondition true
    }
  }

  return@ClassCondition false
}


// replace Kotlin JPS plugin version by a more tailored for Bazel
@Suppress("unused")
class JpsKotlinCompilerRunner {
  companion object {
    private val compilerConstructor: Constructor<*>
    private val exec: Method

    fun triggerInit() {
      requireNotNull(exec)
    }

    init {
      val libDir = getLibPath(computeKotlinPathsForJpsPlugin())
      val jarFiles = sequenceOf(KotlinPaths.Jar.StdLibJdk7, KotlinPaths.Jar.StdLibJdk8, KotlinPaths.Jar.Compiler, KotlinPaths.Jar.Reflect, KotlinPaths.Jar.Trove4j, KotlinPaths.Jar.CoroutinesCore)
        .map { libDir.resolve("${it.baseName}.jar").toFile() }
        .toList()
      val classLoader = ClassPreloadingUtils.preloadClasses(
        jarFiles,
        Preloader.DEFAULT_CLASS_NUMBER_ESTIMATE,
        JpsKotlinCompilerRunner::class.java.classLoader,
        classesToLoadByParent,
      )

      val compiler = Class.forName(KotlinCompilerClass.JVM, true, classLoader)
      exec = compiler.getMethod("execAndOutputXml", PrintStream::class.java, Class.forName("org.jetbrains.kotlin.config.Services", true, classLoader), Array<String>::class.java)
      compilerConstructor = compiler.getDeclaredConstructor()
      compilerConstructor.setAccessible(true)
      exec.setAccessible(true)
    }
  }

  @Suppress("unused")
  fun runK2MetadataCompiler(
    commonArguments: CommonCompilerArguments,
    k2MetadataArguments: K2MetadataCompilerArguments,
    compilerSettings: CompilerSettings,
    environment: JpsCompilerEnvironment,
    destination: String,
    classpath: Collection<String>,
    sourceFiles: Collection<File>,
    buildMetricReporter: JpsBuilderMetricReporter?,
  ) {
    throw UnsupportedOperationException("")
  }

  @Suppress("unused")
  fun runK2JvmCompiler(
    commonArguments: CommonCompilerArguments,
    k2jvmArguments: K2JVMCompilerArguments,
    compilerSettings: CompilerSettings,
    environment: JpsCompilerEnvironment,
    moduleFile: File,
    buildMetricReporter: JpsBuilderMetricReporter?,
  ) {
    val arguments = mergeBeans(commonArguments, XmlSerializerUtil.createCopy(k2jvmArguments))
    setupK2JvmArguments(moduleFile, arguments)
    val stream = ByteArrayOutputStream()
    val out = PrintStream(stream)
    val rc = environment.withProgressReporter { progress ->
      progress.compilationStarted()
      exec.invoke(compilerConstructor.newInstance(), out, environment.services, withAdditionalCompilerArgs(arguments, compilerSettings))
    }
    // exec() returns an ExitCode object, class of which is loaded with a different class loader,
    // so we take its contents through reflection
    val exitCode = ExitCode.valueOf(getReturnCodeFromObject(rc))
    processCompilerOutput(environment.messageCollector, environment.outputItemsCollector, stream, exitCode)
  }
}

private fun withAdditionalCompilerArgs(compilerArgs: CommonCompilerArguments, compilerSettings: CompilerSettings): Array<String> {
  val allArgs = ArgumentUtils.convertArgumentsToStringList(compilerArgs).asSequence() + splitArgumentString(compilerSettings.additionalArguments).asSequence()
  val filteredArguments = mutableListOf<String>()
  val knownPluginOptions = HashSet<String>()
  val argumentsIterator = allArgs.iterator()
  while (argumentsIterator.hasNext()) {
    val argument = argumentsIterator.next()
    // try to find pair -P plugin:<pluginId>:<optionName>=<value>
    if (argument == "-P" && argumentsIterator.hasNext()) {
      val pluginOption = argumentsIterator.next() // expected plugin:<pluginId>:<optionName>=<value>
      val elementIsUnique = knownPluginOptions.add(pluginOption)
      if (elementIsUnique) {
        filteredArguments.add(argument) // add -P
        filteredArguments.add(pluginOption) // add the plugin option
      }
    }
    else {
      // skip filtering for all other arguments
      filteredArguments.add(argument)
    }
  }
  return filteredArguments.toTypedArray()
}

private fun setupK2JvmArguments(moduleFile: File, settings: K2JVMCompilerArguments) {
  settings.buildFile = moduleFile.absolutePath
  settings.destination = null
  settings.noStdlib = true
  settings.noReflect = true
  settings.noJdk = true
}

private fun getReturnCodeFromObject(rc: Any?): String {
  return when {
    rc == null -> ExitCode.INTERNAL_ERROR.toString()
    ExitCode::class.java.name == rc::class.java.name -> rc.toString()
    else -> throw IllegalStateException("Unexpected return: $rc")
  }
}

private const val JPS_KOTLIN_HOME_PROPERTY = "jps.kotlin.home"

internal fun computeKotlinPathsForJpsPlugin(): Path {
  val jpsKotlinHome = System.getProperty(JPS_KOTLIN_HOME_PROPERTY)?.let { Path.of(it) }
    ?: throw RuntimeException("Make sure that '$JPS_KOTLIN_HOME_PROPERTY' system property is set in JPS process")
  if (Files.isDirectory(jpsKotlinHome)) {
    return jpsKotlinHome.resolve("lib")
  }
  else {
    throw RuntimeException("Cannot find kotlinc home at $jpsKotlinHome")
  }
}

private fun getLibPath(libDir: Path): Path {
  if (Files.isDirectory(libDir)) {
    return libDir
  }
  throw IllegalStateException("Broken compiler at '$libDir'. Make sure plugin is properly installed")
}
