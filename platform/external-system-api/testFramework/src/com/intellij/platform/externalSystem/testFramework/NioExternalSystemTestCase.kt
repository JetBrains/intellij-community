// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.testFramework

import com.intellij.UtilBundle
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.externalSystem.service.remote.ExternalSystemProgressNotificationManagerImpl
import com.intellij.openapi.externalSystem.service.remote.ExternalSystemProgressNotificationManagerImpl.Companion.cleanupListeners
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager.Companion.getInstance
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getExternalConfigurationDir
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.*
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.RunAll.Companion.runAll
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.util.ArrayUtilRt
import com.intellij.util.PathUtil
import com.intellij.util.ThrowableRunnable
import com.intellij.util.io.createDirectories
import com.intellij.util.io.createParentDirectories
import com.intellij.util.io.delete
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.SystemIndependent
import java.awt.HeadlessException
import java.io.IOException
import java.lang.reflect.Modifier
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.outputStream
import kotlin.io.path.walk

abstract class NioExternalSystemTestCase : UsefulTestCase() {

  private var project: Project? = null
  val myProject: Project
    get() = project!!

  private var projectConfig: VirtualFile? = null
  val myProjectConfig: VirtualFile
    get() = projectConfig!!

  private var projectRoot: VirtualFile? = null
  val projectPath: String
    get() = projectRoot!!.getPath()

  val myProjectRoot: VirtualFile
    get() = projectRoot!!

  private var allConfigs: MutableList<VirtualFile?> = ArrayList<VirtualFile?>()

  private var wslDistribution: WSLDistribution? = null
  val myWSLDistribution: WSLDistribution?
    get() = wslDistribution

  private var testDir: Path? = null
  val myTestDir: Path
    get() = testDir!!

  private var testFixture: IdeaProjectTestFixture? = null
  var myTestFixture: IdeaProjectTestFixture
    get() = testFixture!!
    set(value) {
      testFixture = value
    }

  private var ourTempDir: Path? = null

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    setUpFixtures()
    project = myTestFixture.getProject()

    setupWsl()
    ensureTempDirCreated()

    val testDirName = "testDir" + System.currentTimeMillis()
    testDir = ourTempDir!!.resolve(testDirName)
    testDir!!.ensureExists()

    EdtTestUtil.runInEdtAndWait<RuntimeException?>(ThrowableRunnable {
      ApplicationManager.getApplication().runWriteAction(Runnable {
        try {
          setUpInWriteAction()
        }
        catch (e: Throwable) {
          try {
            tearDown()
          }
          catch (e1: Exception) {
            e1.printStackTrace()
          }
          throw RuntimeException(e)
        }
      })
    })

    val allowedRoots: MutableList<String> = ArrayList()
    collectAllowedRoots(allowedRoots)
    if (!allowedRoots.isEmpty()) {
      VfsRootAccess.allowRootAccess(myTestFixture.testRootDisposable, *ArrayUtilRt.toStringArray(allowedRoots))
    }
  }

  protected fun setupWsl() {
    val wslMsId = System.getProperty("wsl.distribution.name") ?: return
    val distributions = WslDistributionManager.getInstance().getInstalledDistributions()
    check(!distributions.isEmpty()) { "no WSL distributions configured!" }
    wslDistribution = distributions.first { it: WSLDistribution? -> wslMsId == it!!.msId }
                        ?: throw IllegalStateException("Distribution $wslMsId was not found")
  }

  protected open fun collectAllowedRoots(roots: MutableList<String>): Unit = Unit

  @Throws(IOException::class)
  private fun ensureTempDirCreated() {
    if (ourTempDir != null) return

    if (wslDistribution == null) {
      ourTempDir = Path.of(FileUtil.getTempDirectory()).resolve(getTestsTempDir())
    }
    else {
      ourTempDir = Path.of(wslDistribution!!.getWindowsPath("/tmp")).resolve(getTestsTempDir())
    }

    ourTempDir!!.delete()
    ourTempDir!!.ensureExists()
  }

  protected abstract fun getTestsTempDir(): String?

  @Throws(Exception::class)
  protected open fun setUpFixtures() {
    myTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(name, useDirectoryBasedStorageFormat()).getFixture()
    myTestFixture.setUp()
  }

  protected open fun useDirectoryBasedStorageFormat(): Boolean = false

  @Throws(Exception::class)
  protected open fun setUpInWriteAction(): Unit = setUpProjectRoot()

  @Throws(Exception::class)
  protected fun setUpProjectRoot() {
    val projectDir = testDir!!.resolve("project")
    projectDir.ensureExists()
    projectRoot = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(projectDir)
  }

  @Throws(Exception::class)
  public override fun tearDown() {
    RunAll(
      ThrowableRunnable {
        if (project != null && !project!!.isDisposed()) {
          project!!.getExternalConfigurationDir().delete()
        }
      },
      ThrowableRunnable { EdtTestUtil.runInEdtAndWait<RuntimeException?>(ThrowableRunnable { tearDownFixtures() }) },
      ThrowableRunnable { project = null },
      ThrowableRunnable {
        if (testDir != null) {
          NioFiles.deleteRecursively(testDir!!)
        }
      },
      ThrowableRunnable { ExternalSystemProgressNotificationManagerImpl.assertListenersReleased() },
      ThrowableRunnable { cleanupListeners() },
      ThrowableRunnable { super.tearDown() },
      ThrowableRunnable { resetClassFields(javaClass) }
    ).run()
  }

  protected open fun tearDownFixtures() {
    runAll(
      { myTestFixture.tearDown() },
      { testFixture = null }
    )
  }

  private fun resetClassFields(aClass: Class<*>?) {
    if (aClass == null) return

    val fields = aClass.getDeclaredFields()
    for (field in fields) {
      val modifiers = field.modifiers
      if ((modifiers and Modifier.FINAL) == 0 && (modifiers and Modifier.STATIC) == 0 && !field.type.isPrimitive) {
        field.setAccessible(true)
        try {
          field.set(this, null)
        }
        catch (e: IllegalAccessException) {
          e.printStackTrace()
        }
      }
    }

    if (aClass == NioExternalSystemTestCase::class.java) return
    resetClassFields(aClass.getSuperclass())
  }

  @Throws(Throwable::class)
  override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable?>) {
    try {
      super.runTestRunnable(testRunnable)
    }
    catch (throwable: Exception) {
      var each: Throwable? = throwable
      do {
        if (each is HeadlessException) {
          printIgnoredMessage("Doesn't work in Headless environment")
          return
        }
      }
      while ((each!!.cause.also { each = it }) != null)
      throw throwable
    }
  }

  protected fun path(relativePath: String): @SystemIndependent String =
    PathUtil.toSystemIndependentName(getProjectPath(relativePath).toString())

  protected fun getProjectPath(relativePath: String): Path = Path.of(projectRoot!!.getPath()).resolve(relativePath)

  protected fun resetTestFixture() {
    testFixture = null
  }

  protected fun createModule(name: String?, type: ModuleType<*>): Module? {
    try {
      return WriteCommandAction.writeCommandAction(project).compute<Module?, IOException?>(ThrowableComputable {
        val file = createProjectSubFile("$name/$name.iml")
        val module = getInstance(project!!).newModule(file.getPath(), type.id)
        PsiTestUtil.addContentRoot(module, file.getParent())
        module
      })
    }
    catch (e: IOException) {
      throw RuntimeException(e)
    }
  }

  protected fun createProjectConfig(config: @NonNls String): VirtualFile = createConfigFile(projectRoot!!, config)
    .also { projectConfig = it }

  protected fun createConfigFile(dir: VirtualFile, config: String): VirtualFile {
    val configFileName = getExternalSystemConfigFileName()
    val configFile: VirtualFile
    try {
      configFile = WriteAction.computeAndWait<VirtualFile, IOException?>(ThrowableComputable {
        val file = dir.findChild(configFileName)
        file ?: dir.createChildData(null, configFileName)
      })
      allConfigs.add(configFile)
    }
    catch (e: IOException) {
      throw RuntimeException(e)
    }
    setFileContent(configFile, config, true)
    return configFile
  }

  protected abstract fun getExternalSystemConfigFileName(): String

  @Throws(IOException::class)
  protected fun createProjectSubDirs(vararg relativePaths: String) {
    for (path in relativePaths) {
      createProjectSubDir(path)
    }
  }

  @Throws(IOException::class)
  protected fun createProjectSubDir(relativePath: String): VirtualFile? {
    val directory = getProjectPath(relativePath)
    directory.createDirectories()
    return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(directory)
  }

  @Throws(IOException::class)
  fun createProjectSubFile(relativePath: String): VirtualFile {
    val file = getProjectPath(relativePath)
    file.createParentDirectories()
    if (!file.exists()) {
      file.createFile()
    }
    if (!file.exists()) {
      throw AssertionError("Unable to create the project sub file: $file")
    }
    return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file)!!
  }

  @Throws(IOException::class)
  protected fun createProjectJarSubFile(relativePath: String, vararg contentEntries: Pair<ByteArraySequence?, String?>): VirtualFile {
    assertTrue("Use 'jar' extension for JAR files: '$relativePath'", FileUtilRt.extensionEquals(relativePath, "jar"))
    val file = getProjectPath(relativePath)
    file.createParentDirectories()
    file.createFile()
    if (!file.exists()) {
      throw AssertionError("Unable to create the project sub file: $file")
    }

    val manifest = Manifest()
    manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
    file.outputStream().use { file ->
      JarOutputStream(file, manifest).use { jar ->
        for (contentEntry in contentEntries) {
          addJarEntry(contentEntry.first!!.toBytes(), contentEntry.second!!, jar)
        }
      }
    }
    val virtualFile: VirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file)!!
    assertNotNull(virtualFile)
    val jarFile: VirtualFile = JarFileSystem.getInstance().getJarRootForLocalFile(virtualFile)!!
    assertNotNull(jarFile)
    return jarFile
  }

  @Throws(IOException::class)
  private fun addJarEntry(bytes: ByteArray, path: String, target: JarOutputStream) {
    val entry = JarEntry(path.replace("\\", "/"))
    target.putNextEntry(entry)
    target.write(bytes)
    target.close()
  }

  @Throws(IOException::class)
  open fun createProjectSubFile(relativePath: String, content: String): VirtualFile {
    val file = createProjectSubFile(relativePath)
    setFileContent(file, content, false)
    return file
  }

  protected open fun getModule(name: String): Module = getModule(project!!, name)

  protected fun getModule(project: Project, name: String): Module {
    val m = ReadAction.compute<Module?, RuntimeException?>(ThrowableComputable { getInstance(project).findModuleByName(name) })
    assertNotNull("Module $name not found", m)
    return m!!
  }

  fun setFileContent(file: VirtualFile, content: String, advanceStamps: Boolean) {
    try {
      WriteAction.runAndWait<IOException?>(ThrowableRunnable {
        if (advanceStamps) {
          file.setBinaryContent(content.toByteArray(StandardCharsets.UTF_8), -1, file.getTimeStamp() + 4000)
        }
        else {
          file.setBinaryContent(content.toByteArray(StandardCharsets.UTF_8), file.modificationStamp, file.getTimeStamp())
        }
      })
    }
    catch (e: IOException) {
      throw RuntimeException(e)
    }
  }

  protected fun ignore(): Boolean {
    printIgnoredMessage(null)
    return true
  }

  private fun printIgnoredMessage(message: String?) {
    var toPrint = "Ignored"
    if (message != null) {
      toPrint += ", because $message"
    }
    toPrint += ": ${javaClass.getSimpleName()}.$name"
    println(toPrint)
  }

  companion object {
    @JvmStatic
    fun collectRootsInside(root: String): Collection<String> {
      val roots = mutableListOf<String>()
      roots.add(root)
      Path.of(root).walk()
        .forEach {
          val canonicalPath = it.toCanonicalPath()
          if (!FileUtil.isAncestor(canonicalPath, canonicalPath, false)) {
            roots.add(canonicalPath)
          }
        }
      return roots
    }
  }

  private fun Path.ensureExists() {
    if (!exists()) {
      try {
        createDirectories()
      }
      catch (e: Exception) {
        throw IOException(UtilBundle.message("exception.directory.can.not.create", this), e)
      }
    }
  }
}