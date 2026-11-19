// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.execution.process

import com.intellij.execution.CommandLineWrapperUtil
import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.java.JavaTargetParameter
import com.intellij.execution.target.java.TargetPaths
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.projectRoots.CommandLineWrapperClassProvider
import com.intellij.openapi.projectRoots.JdkCommandLineSetup
import com.intellij.openapi.projectRoots.SimpleJavaSdkType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.util.PathUtil
import com.intellij.util.SystemProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JdkUtilTest : BareTestFixtureTestCase() {
  companion object {
    private val jdk = lazy { SimpleJavaSdkType().createJdk("tmp", SystemProperties.getJavaHome()) }
    private const val WRAPPER_NAME = "com.intellij.rt.execution.CommandLineWrapper"
    private const val AGENT = "-agentpath:"
    private const val ARGS = "=args,file="
    private val AGENT_PATH = Path.of("/","path", "to", "local", "agent").pathString
    private val DUMP_FILE = Path.of("/", "path", "to", "remote", "output").pathString
    private val FULL_AGENT = "$AGENT$AGENT_PATH$ARGS$DUMP_FILE"
  }

  private val parameters = SimpleJavaParameters()
  private var filesToDelete: Collection<File>? = null

  @Before fun setUp() {
    parameters.jdk = jdk.value
    parameters.classPath.add("/classes/hello.jar")
    parameters.vmParametersList.add("-Xmx256m")
    parameters.vmParametersList.add("-Dan.option=1")
    parameters.mainClass = "hello.Main"
    parameters.programParametersList.add("hello")
    parameters.setUseDynamicClasspath(true)
  }

  @After fun tearDown() {
    filesToDelete?.forEach { FileUtil.delete(it) }
  }

  @Test fun noDynamicClasspath() {
    parameters.setUseDynamicClasspath(false)
    doTest("-Xmx256m", "-Dan.option=1", "-classpath", "/classes/hello.jar", "hello.Main", "hello")
  }

  @Test fun noDynamicClasspathInModuleMode() {
    parameters.setUseDynamicClasspath(false)
    setModuleMode()
    doTest("-Xmx256m", "-Dan.option=1", "-p", "/classes/hello.jar", "-m", "hello/hello.Main", "hello")
  }

  @Test fun noDynamicClasspathInJarMode() {
    parameters.setUseDynamicClasspath(false)
    parameters.mainClass = null
    parameters.jarPath = "/classes/main.jar"
    doTest("-Xmx256m", "-Dan.option=1", "-classpath", "/classes/hello.jar", "-jar", "/classes/main.jar", "hello")
  }

  @Test fun `do not use CommandLineWrapper for 'java -jar' command`() {
    parameters.mainClass = null
    parameters.classPath.clear()
    parameters.jarPath = "/classes/main.jar"
    doTest("-Xmx256m", "-Dan.option=1", "-jar", "/classes/main.jar", "hello")
  }

  @Test fun dynamicClasspathWithJar() {
    parameters.isUseClasspathJar = true
    doTest("-Xmx256m", "-Dan.option=1", "-classpath", "#classpath.jar#", "hello.Main", "hello")
  }

  @Test fun dynamicClasspathWithJarAndParameters() {
    parameters.isUseClasspathJar = true
    parameters.setUseDynamicVMOptions(true)
    parameters.setUseDynamicParameters(true)
    doTest("-Xmx256m", "-classpath", "#idea_rt#:#classpath.jar#", WRAPPER_NAME, "#classpath.jar#", "hello.Main")
  }

  @Test fun dynamicClasspathWithWrapper() {
    parameters.isUseClasspathJar = false
    doTest("-Xmx256m", "-Dan.option=1", "-classpath", "#idea_rt#", WRAPPER_NAME, "#classpath#", "hello.Main", "hello")
  }

  @Test fun dynamicClasspathWithWrapperAndParameters() {
    parameters.isUseClasspathJar = false
    parameters.setUseDynamicVMOptions(true)
    parameters.setUseDynamicParameters(true)
    doTest("-Xmx256m", "-classpath", "#idea_rt#", WRAPPER_NAME, "#classpath#", "@vm_params", "#vm_params#", "@app_params", "#app_params#", "hello.Main")
  }

  @Test fun dynamicClasspathWithArgFile() {
    setModuleMode()
    doTest("-Xmx256m", "-Dan.option=1", "#arg_file#", "-m", "hello/hello.Main", "hello")
    val content = filesToDelete?.find { it.name.contains("idea_arg_file") }?.readLines()?.dropWhile { it.contains("-p") }
    val modulePath = content?.first()
    assertNotNull(modulePath)
    assertTrue { modulePath.isNotEmpty() }
  }

  @Test fun dynamicClasspathWithArgFileAndParameters() {
    setModuleMode()
    parameters.setUseDynamicVMOptions(true)
    parameters.setUseDynamicParameters(true)
    parameters.programParametersList.clearAll()
    parameters.programParametersList.addAll("1#1", "\"2'", "line\n-", "C:\\", "D:\\work", "E:\\work space")
    doTest("#arg_file#")
    val args = filesToDelete?.find { it.name.contains("idea_arg_file") }?.readLines()?.dropWhile { !it.contains("hello.Main") }
    assertThat(args).containsExactly("hello/hello.Main", "1\"#\"1", "\"\\\"\"2\"'\"", "line\"\\n\"-", "C:\\", "D:\\work", "E:\\work\" \"space")
  }

  @Test fun `target-dependent parameter with dynamic VM options in classpath jar`() {
    parameters.isUseClasspathJar = true
    parameters.setUseDynamicVMOptions(true)
    parameters.setUseDynamicParameters(true)
    addTargetDependentAgent()
    doTest("-Xmx256m", FULL_AGENT, "-classpath", "#idea_rt#:#classpath.jar#", WRAPPER_NAME, "#classpath.jar#", "hello.Main")
  }

  @Test fun `target-dependent parameter with dynamic VM options in wrapper file`() {
    parameters.isUseClasspathJar = false
    parameters.setUseDynamicVMOptions(true)
    parameters.setUseDynamicParameters(true)
    addTargetDependentAgent()
    doTest("-Xmx256m", FULL_AGENT, "-classpath", "#idea_rt#", WRAPPER_NAME, "#classpath#", "@vm_params", "#vm_params#", "@app_params", "#app_params#", "hello.Main")
  }

  @Test fun `target-dependent parameter with dynamic VM options in arg file`() {
    setModuleMode()
    parameters.setUseDynamicVMOptions(true)
    addFixedTargetParameter()
    doTest("#arg_file#", "-m", "hello/hello.Main", "hello")
    assertThat(argFileLines()).contains(FULL_AGENT)
  }

  @Test fun `target-dependent VM option precedes main class in arg file`() {
    setModuleMode()
    parameters.setUseDynamicVMOptions(true)
    parameters.setUseDynamicParameters(true)
    addFixedTargetParameter()
    doTest("#arg_file#")
    assertThat(argFileLines()).containsSubsequence(FULL_AGENT, "-m", "hello/hello.Main", "hello")
  }

  @Test fun `target-dependent VM option stays on command line when VM options are not dynamic`() {
    setModuleMode()
    parameters.setUseDynamicParameters(true)
    addFixedTargetParameter()
    doTest("-Xmx256m", "-Dan.option=1", FULL_AGENT, "#arg_file#")
    assertThat(argFileLines()).doesNotContain(FULL_AGENT).containsSubsequence("-m", "hello/hello.Main", "hello")
  }

  @Test fun `target-dependent parameter stays on command line with arg file classpath`() {
    setModuleMode()
    addFixedTargetParameter()
    doTest("-Xmx256m", "-Dan.option=1", FULL_AGENT, "#arg_file#", "-m", "hello/hello.Main", "hello")
    assertThat(argFileLines()).doesNotContain(FULL_AGENT)
  }

  @Test fun `arg file waits for target paths`() {
    setModuleMode()
    parameters.setUseDynamicVMOptions(true)
    parameters.setUseDynamicParameters(true)
    val callbacks = mutableListOf<String>()
    parameters.targetDependentParameters.addParameter {
      val paths = TargetPaths.ordered {
        upload(AGENT_PATH, afterUploadResolved = { callbacks += "upload:$it" })
        download(DUMP_FILE, afterDownloadResolved = { callbacks += "download:$it" })
      }
      JavaTargetParameter.Builder(paths).fixed(AGENT).toResolve(AGENT_PATH).fixed(ARGS).toResolve(DUMP_FILE).build()
    }
    doTest("#arg_file#")
    assertThat(callbacks).containsExactlyInAnyOrder("upload:$AGENT_PATH", "download:$DUMP_FILE")
    assertThat(argFileLines()).containsSubsequence(FULL_AGENT, "-m", "hello/hello.Main", "hello")
  }

  @Test fun `target-dependent parameter on full command line`() {
    parameters.setUseDynamicClasspath(false)
    addFixedTargetParameter()
    doTest("-Xmx256m", "-Dan.option=1", FULL_AGENT, "-classpath", "/classes/hello.jar", "hello.Main", "hello")
  }

  @Test fun `target-dependent parameter with explicit classpath`() {
    parameters.vmParametersList.addAll("-classpath", "/explicit")
    addFixedTargetParameter()
    doTest("-Xmx256m", "-Dan.option=1", "-classpath", "/explicit", FULL_AGENT, "hello.Main", "hello")
  }

  @Test fun `target-dependent parameter with jar`() {
    parameters.mainClass = null
    parameters.classPath.clear()
    parameters.jarPath = "/classes/main.jar"
    addFixedTargetParameter()
    doTest("-Xmx256m", "-Dan.option=1", FULL_AGENT, "-jar", "/classes/main.jar", "hello")
  }

  @Test fun `target-dependent parameter without wrapper provider`() {
    ExtensionTestUtil.maskExtensions(CommandLineWrapperClassProvider.EP_NAME, emptyList(), testRootDisposable)
    addFixedTargetParameter()
    doTest("-Xmx256m", "-Dan.option=1", FULL_AGENT, "-classpath", "/classes/hello.jar", "hello.Main", "hello")
  }

  @Test fun `legacy API keeps target-dependent parameter on full command line`() {
    parameters.setUseDynamicClasspath(false)
    addFixedTargetParameter()
    doTest("-Xmx256m", "-Dan.option=1", FULL_AGENT, "-classpath", "/classes/hello.jar", "hello.Main", "hello", legacy = true)
  }

  @Test fun `legacy API puts target-dependent VM option in arg file`() {
    setModuleMode()
    parameters.setUseDynamicVMOptions(true)
    parameters.setUseDynamicParameters(true)
    addFixedTargetParameter()
    doTest("#arg_file#", legacy = true)
    assertThat(argFileLines()).containsSubsequence(FULL_AGENT, "-m", "hello/hello.Main", "hello")
  }

  @Test fun `the Java plugin gives CommandLineWrapper`() {
    assertThat(JdkCommandLineSetup.commandLineWrapperClass()?.name).isEqualTo(WRAPPER_NAME)
  }

  @Test fun `no CommandLineWrapper without a provider`() {
    ExtensionTestUtil.maskExtensions(CommandLineWrapperClassProvider.EP_NAME, emptyList(), testRootDisposable)
    assertThat(JdkCommandLineSetup.commandLineWrapperClass()).isNull()
  }

  @Test fun javaParametersFileGeneration() {
    val file = FileUtil.createTempFile("test", "javaParametersFileGeneration")
    filesToDelete = setOf(file)

    val args = listOf("1#1", "\"2'", "line\n-", "C:\\", "D:\\work", "E:\\work space", "unicode\u2026")
    CommandLineWrapperUtil.writeArgumentsFile(file, args, StandardCharsets.UTF_8)
    val actual = file.readLines(Charsets.UTF_8)

    assertThat(actual).containsExactly("1\"#\"1", "\"\\\"\"2\"'\"", "line\"\\n\"-", "C:\\", "D:\\work", "E:\\work\" \"space", "unicode\u2026")
  }

  private fun doTest(vararg expected: String, legacy: Boolean = false) {
    val commands = if (legacy) {
      parameters.toCommandLine().getCommandLineList(null).also { args ->
        filesToDelete = args.filter { it.startsWith("@") }.map { File(it.substring(1)) }
      }
    }
    else {
      val request = LocalTargetEnvironmentRequest()
      val cmd = parameters.toCommandLine(request)
      request.prepareEnvironment(TargetProgressIndicator.EMPTY)
      filesToDelete = cmd.filesToDeleteOnTermination
      cmd.build().collectCommandsSynchronously()
    }
    val actual = ParametersList()
    actual.addAll(commands)
    val toCompare = mutableListOf<String>()
    actual.parameters.forEachIndexed { i, arg ->
      if (i > 0 && !arg.contains(".encoding=")) {
        toCompare += when {
          arg.contains(File.pathSeparatorChar) -> arg.splitToSequence(File.pathSeparatorChar).map { mapPath(it) }.joinToString(":")
          arg.contains(File.separatorChar) -> mapPath(arg)
          else -> arg
        }
      }
    }

    assertThat(toCompare).containsExactly(*expected)
  }

  private fun mapPath(p: String): String {
    val path = p.replace(File.separatorChar, '/')
    val tempDir = FileUtil.getTempDirectory().replace(File.separatorChar, '/')
    val wrapperClass = JdkCommandLineSetup.commandLineWrapperClass()
    return when {
      path.matches("$tempDir/classpath\\d*.jar".toRegex()) -> "#classpath.jar#"
      path.matches("$tempDir/idea_classpath\\d*".toRegex()) -> "#classpath#"
      path.matches("$tempDir/idea_vm_params\\d*".toRegex()) -> "#vm_params#"
      path.matches("$tempDir/idea_app_params\\d*".toRegex()) -> "#app_params#"
      path.matches("@$tempDir/idea_arg_file\\d*".toRegex()) -> "#arg_file#"
      wrapperClass != null && FileUtil.pathsEqual(path, PathUtil.getJarPathForClass(wrapperClass)) -> "#idea_rt#"
      else -> path
    }
  }

  private fun addTargetDependentAgent() {
    parameters.targetDependentParameters.addParameter {
      JavaTargetParameter.Builder(uploadPaths = setOf(AGENT_PATH),
                                  downloadPaths = setOf(DUMP_FILE))
        .fixed(AGENT)
        .toResolve(AGENT_PATH)
        .fixed(ARGS)
        .toResolve(DUMP_FILE)
        .build()
    }
  }

  private fun addFixedTargetParameter() {
    parameters.targetDependentParameters.addParameter { JavaTargetParameter.fixed(FULL_AGENT) }
  }

  private fun argFileLines(): List<String> = filesToDelete!!.single { it.name.contains("idea_arg_file") }.readLines()

  private fun setModuleMode() {
    parameters.modulePath.addAll(parameters.classPath.pathList)
    parameters.classPath.clear()
    parameters.moduleName = "hello"
    parameters.isArgFile = true
  }
}
