// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.model

import com.intellij.openapi.application.PathManager
import com.intellij.testGuiFramework.fixtures.JDialogFixture
import com.intellij.testGuiFramework.framework.GuiTestUtil.defaultTimeout
import com.intellij.testGuiFramework.framework.GuiTestUtil.fileSearchAndReplace
import com.intellij.testGuiFramework.impl.*
import com.intellij.testGuiFramework.util.*
import com.intellij.testGuiFramework.util.scenarios.*
import org.fest.swing.exception.ComponentLookupException
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import org.hamcrest.core.Is.`is` as Matcher_Is

/**
 * Creates a Java project with a specified framework
 * @param projectPath full path where the new project should be created
 * last item in the path is considered as a new project name
 * @param framework framework name, if empty - no framework should be selected
 */
fun KotlinGuiTestCase.createJavaProject(
  projectPath: String,
  framework: LibrariesSet = emptySet()) {
  welcomePageDialogModel.createNewProject()
  newProjectDialogModel.assertGroupPresent(NewProjectDialogModel.Groups.Kotlin)
  newProjectDialogModel.createJavaProject(projectPath, framework)
  waitAMoment()
}

/**
 * Creates a Gradle project with a specified framework
 * @param projectPath full path where the new project should be created with project name
 * @param group groupid of created gradle project
 * @param artifact artifactid of created gradle project
 * @param framework framework name, if empty - no framework should be selected
 * Note: debugged only with Kotlin frameworks
 */
fun KotlinGuiTestCase.createGradleProject(
  projectPath: String,
  gradleOptions: NewProjectDialogModel.GradleProjectOptions
) {
  welcomePageDialogModel.createNewProject()
  newProjectDialogModel.assertGroupPresent(NewProjectDialogModel.Groups.Kotlin)
  newProjectDialogModel.createGradleProject(projectPath, gradleOptions)
}


/**
 * Creates a Maven project with a specified archetype
 * @param projectPath full path where the new project should be created with project name
 * @param artifact artifactid of created gradle project
 * @param archetype archetype name, if empty - no archetype should be selected
 * @param kotlinVersion version of chosen archetype
 * Note: debugged only with Kotlin frameworks
 */
fun KotlinGuiTestCase.createMavenProject(
  projectPath: String,
  artifact: String,
  archetype: String = "",
  kotlinVersion: String = ""
) {
  welcomePageDialogModel.createNewProject()
  newProjectDialogModel.assertGroupPresent(NewProjectDialogModel.Groups.Kotlin)

  val mavenOptions = NewProjectDialogModel.MavenProjectOptions(
    artifact = artifact,
    useArchetype = archetype.isNotEmpty(),
    archetypeGroup = "org.jetbrains.kotlin:, $archetype",
    archetypeVersion = "$archetype, :$kotlinVersion"
  )
  newProjectDialogModel.createMavenProject(projectPath, mavenOptions)
}

/**
 * Creates a KOtlin project with a specified framework
 * @param projectPath full path where the new project should be created
 * last item in the path is considered as a new project name
 * @param kotlinKind kind of Kotlin project JVM or JS
 */
fun KotlinGuiTestCase.createKotlinProject(
  projectPath: String,
  kotlinKind: KotlinKind) {
  welcomePageDialogModel.createNewProject()
  newProjectDialogModel.assertGroupPresent(NewProjectDialogModel.Groups.Kotlin)
  newProjectDialogModel.createKotlinProject(projectPath, kotlinLibs.getValue(kotlinKind).kotlinProject.frameworkName)
}

/**
 * Configure Kotlin JVM in a java project
 * @param libInPlugin
 *     - true - the kotlin specific jar files are taken from plugin
 *     - false - needed jar files are created in the `lib` folder within the project folder
 * */
fun KotlinGuiTestCase.configureKotlinJvm(libInPlugin: Boolean) {
  ideFrame {
    waitAMoment(3000)
    logTestStep("Open 'Configure Kotlin in Project' dialog")
    invokeMainMenu("ConfigureKotlinInProject")
    dialog("Create Kotlin Java Runtime Library") {
      if (libInPlugin) {
        logUIStep("Select `Use library from plugin` option")
        radioButton("Use library from plugin").select()
      }
      logUIStep("Close 'Configure Kotlin in Project' dialog with OK")
      button("OK").click()
    }
    waitAMoment()
  }
}

/**
 * Configure Kotlin JS in a project
 * @param libInPlugin
 *     - true - the kotlin specific jar files are taken from plugin
 *     - false - needed jar files are created in the `lib` folder within the project folder
 * */
fun KotlinGuiTestCase.configureKotlinJs(libInPlugin: Boolean) {
  ideFrame {
    waitAMoment()
    logTestStep("Open 'Configure Kotlin (JavaScript) in Project' dialog")
    invokeMainMenu("ConfigureKotlinJsInProject")
    dialog("Create Kotlin JavaScript Library") {
      if (libInPlugin) {
        logUIStep("Select `Use library from plugin` option")
        radioButton("Use library from plugin").select()
      }
      logUIStep("Close 'Configure Kotlin in Project' dialog with OK")
      button("OK").click()
    }
    waitAMoment()
  }
}

/**
 * As list of kotlin versions shown in the configure dialog is filled up from the internet
 * sometimes it's not loaded. In such cases another attempt is performed.
 * @param logText - logged text
 * @param menuTitle - invoked menu ID
 * @param dialogTitle - title of the configuring dialog (all dialogs are the same, but titles)
 * @param kotlinVersion - kotlin version as it should be added to build.gradle/pom.xml
 * @param module - if empty - all modules should be configured
 *                 else a single module with the specified name should be configured
 * */
fun KotlinGuiTestCase.configureKotlinFromGradleMaven(logText: String,
                                                     menuTitle: String,
                                                     dialogTitle: String,
                                                     kotlinVersion: String,
                                                     module: String) {
  var result: Boolean = false
  val maxAttempts = 3
  ideFrame {
    var counter = 0
    do {
      try {
        logTestStep("$logText. Attempt #${counter + 1}")
        waitAMoment()
        invokeMainMenu(menuTitle)
        result = configureKotlinFromGradleMavenSelectValues(dialogTitle, kotlinVersion, module)
        counter++
      }
      catch (e: ComponentLookupException) {}
    }
    while (!result && counter < maxAttempts)
    waitAMoment()
  }
  assert(result) { "Version $kotlinVersion not found after $maxAttempts attempts" }
}

/**
 * Configure Kotlin JVM in a project based on gradle/maven
 * @param dialogTitle - title of the configuring dialog (all dialogs are the same, but titles)
 * @param kotlinVersion - kotlin version as it should be added to build.gradle/pom.xml
 * @param module - if empty - all modules should be configured
 *                 else a single module with the specified name should be configured
 *  @return true if configuration passed correctly, false in case of any errors, for example
 *  if required [kotlinVersion] is absent in the versions list.
 * TODO: add setting of specified module name and kotlin version
 * */
fun KotlinGuiTestCase.configureKotlinFromGradleMavenSelectValues(
  dialogTitle: String,
  kotlinVersion: String,
  module: String = ""): Boolean {
  var result = false
  dialog(dialogTitle) {
    if (module.isEmpty()) {
      logUIStep("Select `All modules` option")
      radioButton("All modules").select()
    }
    else {
      logUIStep("Select `Single module` option")
      radioButton("Single module:").select()
    }
    waitUntil { button("OK").isEnabled }
    val cmb = combobox("Kotlin compiler and runtime version:")
    if (cmb.listItems().contains(kotlinVersion)) {
      logTestStep("Select kotlin version `$kotlinVersion`")
      if (cmb.selectedItem() != kotlinVersion) {
        cmb
          .expand()
          .selectItem(kotlinVersion)
        logInfo("Combobox `Kotlin compiler and runtime version`: current selected is ${cmb.selectedItem()} ")
      }
      logUIStep("Close Configure Kotlin dialog with OK")
      button("OK").click()
      result = true
    }
    else {
      logUIStep("Close Configure Kotlin dialog with Cancel")
      button("Cancel").click()
    }
  }
  return result
}

fun KotlinGuiTestCase.configureKotlinJvmFromGradle(
  kotlinVersion: String,
  module: String = "") {
  configureKotlinFromGradleMaven(
    logText = "Open `Configure Kotlin with Java with Gradle` dialog",
    menuTitle = "ConfigureKotlinInProject",
    dialogTitle = "Configure Kotlin with Java with Gradle",
    kotlinVersion = kotlinVersion,
    module = module)
}

fun KotlinGuiTestCase.configureKotlinJsFromGradle(
  kotlinVersion: String,
  module: String = "") {
  configureKotlinFromGradleMaven(
    logText = "Open `Configure Kotlin with JavaScript with Gradle` dialog",
    menuTitle = "ConfigureKotlinJsInProject",
    dialogTitle = "Configure Kotlin with JavaScript with Gradle",
    kotlinVersion = kotlinVersion,
    module = module)
}

fun KotlinGuiTestCase.configureKotlinJvmFromMaven(
  kotlinVersion: String,
  module: String = "") {
  configureKotlinFromGradleMaven(
    logText = "Open `Configure Kotlin with Java with Maven` dialog",
    menuTitle = "ConfigureKotlinInProject",
    dialogTitle = "Configure Kotlin with Java with Maven",
    kotlinVersion = kotlinVersion,
    module = module)
}

fun KotlinGuiTestCase.configureKotlinJsFromMaven(
  kotlinVersion: String,
  module: String = "") {
  configureKotlinFromGradleMaven(
    logText = "Open `Configure Kotlin with JavaScript with Maven` dialog",
    menuTitle = "ConfigureKotlinJsInProject",
    dialogTitle = "Configure Kotlin with JavaScript with Maven",
    kotlinVersion = kotlinVersion,
    module = module)
}

/**
 * Opens Project Structure dialog and Library tab
 * Checks that an appropriate Kotlin library is created with a certain set of jar files
 * what are expected to be taken from the project folder
 * @param projectPath full path to the project
 * @param kotlinKind kotlin kind (JVM or JS)
 * */
fun ProjectStructureDialogScenarios.checkKotlinLibsInStructureFromProject(
  projectPath: String,
  kotlinKind: KotlinKind) {
  val expectedJars = getKotlinLibInProject(projectPath)
    .map { projectPath + File.separator + "lib" + File.separator + it }
  val expectedLibName = kotlinLibs[kotlinKind]!!.kotlinProject.libName!!
  openProjectStructureAndCheck {
    projectStructureDialogModel.checkLibrariesFromIDEA(
      expectedLibName,
      expectedJars
    )
  }
}

/**
 * Opens Project Structure dialog and Library tab
 * Checks that an appropriate Kotlin library is created with a certain set of jar files
 * what are expected to be taken from the plugin
 * @param kotlinKind kotlin kind (JVM or JS)
 * */
fun ProjectStructureDialogScenarios.checkKotlinLibsInStructureFromPlugin(
  kotlinKind: KotlinKind,
  kotlinVersion: String) {
  val expectedLibName = kotlinLibs[kotlinKind]!!.kotlinProject.libName!!
  val configPath = PathManager.getConfigPath().normalizeSeparator()
  val expectedJars = kotlinLibs[kotlinKind]!!
    .kotlinProject
    .jars
    .getJars(kotlinVersion)
    .map { configPath + pathKotlinInConfig + File.separator + it }
  openProjectStructureAndCheck {
    projectStructureDialogModel.checkLibrariesFromIDEA(
      expectedLibName,
      expectedJars
    )
  }
}

/**
 * Checks that a certain set of jar files is copied to the project folder
 * @param projectPath full path to the project folder
 * @param kotlinKind kotlin kind (JVM or JS)
 * */
fun KotlinGuiTestCase.checkKotlinLibInProject(projectPath: String,
                                              kotlinKind: KotlinKind,
                                              kotlinVersion: String) {
  val expectedLibs = kotlinLibs[kotlinKind]?.kotlinProject?.jars?.getJars(kotlinVersion) ?: return
  val actualLibs = getKotlinLibInProject(projectPath)

  expectedLibs.forEach {
    logInfo("check if expected '$it' is present")
    //    collector.checkThat( actualLibs.contains(it), Matcher_Is(true) ) { "Expected, but absent file: $it" }
    assert(actualLibs.contains(it)) { "Expected, but absent file: $it" }
  }

  actualLibs.forEach {
    logInfo("check if existing '$it' is expected")
    //    collector.checkThat( expectedLibs.contains(it), Matcher_Is(true) ) { "Unexpected file: $it" }
    assert(expectedLibs.contains(it)) { "Unexpected file: $it" }
  }

}

fun KotlinGuiTestCase.createKotlinFile(
  projectName: String,
  packageName: String = "src",
  fileName: String) {
  ideFrame {
    waitAMoment()
    logTestStep("Create a Kotlin file `$fileName`")
    toolwindow(id = "Project") {
      projectView {
        val treePath = listOf(projectName, *packageName.split("/", "").toTypedArray()).toTypedArray()
        logUIStep("Click on the path: ")
        treePath.forEach { logInfo("   $it") }
        path(*treePath).click()
        waitAMoment()

        logUIStep("Invoke menu kotlin -> new file and open `New Kotlin File/Class` dialog")
        invokeMainMenu("Kotlin.NewFile")
      }
    }
    dialog("New Kotlin File/Class") {
      logUIStep("Fill `Name` with `$fileName`")
      textfield("Name:").click()
      typeText(fileName)
      logUIStep("Close `New Kotlin File/Class` dialog with OK")
      button("OK").click()
    }
    waitAMoment()
  }
}

fun KotlinGuiTestCase.makeTestRoot(projectPath: String, testRoot: String) {
  ideFrame {
    projectView {
      path(projectPath, testRoot).doubleClick()
      path(projectPath, testRoot).rightClick()
    }
    popup("Mark Directory as", "Test Sources Root")
  }
}

fun KotlinGuiTestCase.editorSearchAndReplace(isRegex: Boolean, isReplaceAll: Boolean, search: String, vararg replace: String) {
  ideFrame {
    editor {
      logTestStep("Change `$search` with `${replace.joinToString(" \\n ")}` in the currently open editor")
      // Magic number to click to the file
      // Problem: on HighDPI monitor moveTo(1) sometimes doesn't click to the file
      waitAMoment()
      moveTo(1)
      waitAMoment()
      shortcut(Modifier.CONTROL + Key.R)
      if (checkbox("Regex").isSelected != isRegex) {
        logUIStep("Change state of `Regex` option")
        checkbox("Regex").click()
        if (checkbox("Regex").isSelected != isRegex) {
          logUIStep("Change state of `Regex` option. Attempt #2")
          checkbox("Regex").click()
        }
      }
      logUIStep("Search field: type `$search`")
      typeText(search)
      shortcut(Key.TAB)
      for ((ind, str) in replace.withIndex()) {
        logUIStep("Replace field: type `$str`")
        typeText(str)
        if (ind < replace.size - 1) {
          logUIStep("Replace field: press Ctrl+Shift+Enter to add a new line")
          shortcut(
            Modifier.CONTROL + Modifier.SHIFT + Key.ENTER)
        }
      }
    }
    if (isReplaceAll)
      button("Replace all").click()
    else
      button("Replace").click()
    logUIStep("Close Search and Replace banner with Cancel")
    shortcut(Key.ESCAPE)
    // TODO: Remove Ctrl+Home after GUI-73 fixing
    logUIStep("Put text cursor to the begin")
    shortcut(Modifier.CONTROL + Key.HOME)
    editorClearSearchAndReplace()
  }
}

fun KotlinGuiTestCase.editorClearSearchAndReplace() {
  ideFrame {
    editor {
      logTestStep("Clear search and replace fields in the currently open editor")
      waitAMoment()
      moveTo(1)
      waitAMoment()
      shortcut(Modifier.CONTROL + Key.R)
      shortcut(Key.DELETE)
      shortcut(Key.TAB)
      shortcut(Key.DELETE)
    }
    logUIStep("Close Search and Replace banner with Cancel")
    shortcut(Key.ESCAPE)
  }
}

fun KotlinGuiTestCase.addDevRepositoryToBuildGradle(fileName: String, isKotlinDslUsed: Boolean) {
  val mavenCentral = "mavenCentral()"
  val urlGDsl = "maven { url 'https://dl.bintray.com/kotlin/kotlin-dev' }"
  val urlKDsl = "maven { setUrl (\"https://dl.bintray.com/kotlin/kotlin-dev/\") }"
  if (isKotlinDslUsed)
    fileSearchAndReplace(fileName = fileName) {
      if(it.contains(mavenCentral))
        listOf(mavenCentral, urlKDsl).joinToString(separator = "\n")
      else it
    }
  else
    fileSearchAndReplace(fileName = fileName) {
      if(it.contains(mavenCentral))
        listOf(mavenCentral, urlGDsl).joinToString(separator = "\n")
      else it
    }
}

fun KotlinGuiTestCase.addDevRepositoryToPomXml(fileName: String) {
  val searchedLine = """</dependencies>"""
  val changingLine = """
    <repositories>
        <repository>
            <id>kotlin-dev</id>
            <url>https://dl.bintray.com/kotlin/kotlin-dev</url>
            <releases><enabled>true</enabled></releases>
            <snapshots><enabled>false</enabled></snapshots>
        </repository>
    </repositories>

    <pluginRepositories>
        <pluginRepository>
            <id>kotlin-dev</id>
            <url>https://dl.bintray.com/kotlin/kotlin-dev</url>
            <releases><enabled>true</enabled></releases>
            <snapshots><enabled>false</enabled></snapshots>
        </pluginRepository>
    </pluginRepositories>
    """.split("\n").toTypedArray()
  fileSearchAndReplace(fileName = fileName) {
    if(it.contains(searchedLine))
      listOf(searchedLine, *changingLine).joinToString(separator = "\n")
    else it
  }
}

fun KotlinGuiTestCase.changeKotlinVersionInBuildGradle(fileName: String,
                                                       isKotlinDslUsed: Boolean,
                                                       kotlinVersion: String) {
  fileSearchAndReplace( fileName = fileName){
    if (it.contains("kotlin")){
      val regex = """(id|kotlin)\s?\(?[\'\"](.*)[\'\"]\)? version [\'\"](.*)[\'\"]"""
        .trimIndent()
        .toRegex(RegexOption.IGNORE_CASE)
      if(regex.find(it) != null)
      it.replace(regex.find(it)!!.groupValues[3], kotlinVersion)
      else it
    }
    else it
  }
}

fun KotlinGuiTestCase.changeKotlinVersionInPomXml(fileName: String, kotlinVersion: String) {
  val oldVersion = "<kotlin\\.version>.+<\\/kotlin\\.version>"
  val newVersion = "<kotlin.version>$kotlinVersion</kotlin.version>"
  fileSearchAndReplace(fileName = fileName) {
    if(it.contains(oldVersion.toRegex(RegexOption.IGNORE_CASE)))
      newVersion
    else it
  }
}

fun KotlinGuiTestCase.openFileFromProjectView(vararg fileName: String) {
  ideFrame {
    projectView {
      logTestStep("Open ${fileName.toList()}")
      path(*fileName).click()
      shortcut(Key.RIGHT)
      path(*fileName).click()
      logUIStep("clicked on the path ${fileName.toList()} and going to double click it")
      waitAMoment()
      path(*fileName).doubleClick()
    }
  }
}

fun KotlinGuiTestCase.openBuildGradle(isKotlinDslUsed: Boolean, vararg projectName: String) {
  val buildGradleName = "build.gradle${if (isKotlinDslUsed) ".kts" else ""}"
  openFileFromProjectView(*projectName, buildGradleName)
}

fun KotlinGuiTestCase.openPomXml(vararg projectName: String) {
  openFileFromProjectView(*projectName, "pom.xml")
}

fun KotlinGuiTestCase.editSettingsGradle(){
  //   if project is configured to old Kotlin version, it must be released and no changes are required in the settings.gradle file
  if (!KotlinTestProperties.isActualKotlinUsed()) return
  val fileName = "$projectFolder/settings.gradle"
  if (KotlinTestProperties.isArtifactOnlyInDevRep) addDevRepositoryToBuildGradle(fileName, isKotlinDslUsed = false)
}

fun KotlinGuiTestCase.editBuildGradle(
  kotlinVersion: String,
  isKotlinDslUsed: Boolean = false,
  vararg projectName: String = arrayOf()) {
  //   if project is configured to old Kotlin version, it must be released and no changes are required in the build.gradle file
  if (!KotlinTestProperties.isActualKotlinUsed()) return

  val innerPath = if (projectName.isNotEmpty()) "/" + projectName.joinToString(separator = "/") else ""
  val fileName = projectFolder + innerPath + "/build.gradle${if (isKotlinDslUsed) ".kts" else ""}"
  logTestStep("Going to edit $fileName")

  if (KotlinTestProperties.isArtifactOnlyInDevRep) addDevRepositoryToBuildGradle(fileName, isKotlinDslUsed)
  if (!KotlinTestProperties.isArtifactPresentInConfigureDialog && KotlinTestProperties.kotlin_plugin_version_main != kotlinVersion)
    changeKotlinVersionInBuildGradle(fileName, isKotlinDslUsed, kotlinVersion)
}

fun KotlinGuiTestCase.editPomXml(kotlinVersion: String,
                                 kotlinKind: KotlinKind,
                                 vararg projectName: String = arrayOf()) {
  //   if project is configured to old Kotlin version, it must be released and no changes are required in the pom.xml file
  if (!KotlinTestProperties.isActualKotlinUsed()) return

  val innerPath = if (projectName.isNotEmpty()) "/" + projectName.joinToString(separator = "/") else ""
  val fileName = "$projectFolder$innerPath/pom.xml"
  logTestStep("Going to edit $fileName")

  if (KotlinTestProperties.isArtifactOnlyInDevRep) addDevRepositoryToPomXml(fileName)
  if (!KotlinTestProperties.isArtifactPresentInConfigureDialog && KotlinTestProperties.kotlin_plugin_version_main != kotlinVersion)
    changeKotlinVersionInPomXml(fileName, kotlinVersion)
}

fun getVersionFromString(versionString: String): LanguageVersion {
  val match = """^\d+\.\d+""".toRegex().find(versionString) ?: throw IllegalArgumentException(
    "Incorrect version of Kotlin artifact '$versionString'")
  val result = match.groups[0]!!.value
  return LanguageVersion.valueFromString(result)
}

fun KotlinGuiTestCase.checkFacetState(facet: FacetStructure) {
  fun <T> checkValueWithLog(title: String, expectedValue: T, actualValue: T) {
    val message = "Option: '$title', expected: '$expectedValue', actual: '$actualValue'"
    logInfo(message)
    assert(actualValue == expectedValue) { message }
  }

  dialogWithoutClosing("Project Structure") {

    fun checkCombobox(title: String, expectedValue: String) {
      checkValueWithLog(title, expectedValue, actualValue = combobox(title).selectedItem() ?: "")
    }

    fun checkCheckbox(title: String, expectedValue: Boolean) {
      checkValueWithLog(title, expectedValue, actualValue = checkbox(title).target().isSelected)
    }

    fun checkTextfield(title: String, expectedValue: String) {
      checkValueWithLog(title, expectedValue, actualValue = textfield(title).text() ?: "")
    }

    checkCombobox("Target platform: ", facet.targetPlatform.toString())
    checkCheckbox("Report compiler warnings", facet.reportCompilerWarnings)
    checkCombobox("Language version", facet.languageVersion.toString())
    checkCombobox("API version", facet.apiVersion.toString())
    checkTextfield("Additional command line parameters:", facet.cmdParameters)
    if (facet.jvmOptions != null) {
      checkTextfield("Script template classes:", facet.jvmOptions.templateClasses)
      checkTextfield("Script templates classpath:", facet.jvmOptions.templatesClassPath)
    }
    if (facet.jsOptions != null) {
      checkCheckbox("Generate source maps", facet.jsOptions.generateSourceMap)
      checkTextfield("Add prefix to paths in source map:", facet.jsOptions.sourceMapPrefix)
      checkCombobox("Embed source code into source map:", facet.jsOptions.embedSourceCode2Map.toString())
      checkTextfield("File to prepend to generated code:", facet.jsOptions.fileToPrepend)
      checkTextfield("File to append to generated code:", facet.jsOptions.fileToAppend)
      checkCombobox("Module kind:", facet.jsOptions.moduleKind.toString())
      checkCheckbox("Copy library runtime files", facet.jsOptions.copyLibraryRuntimeFiles)
      checkTextfield("Destination directory", facet.jsOptions.destinationDirectory)
      val runtimeLibs = checkbox("Copy library runtime files").target().isSelected
      val destDirEnabled = textfield("Destination directory").isEnabled
      assert(
        runtimeLibs == destDirEnabled) { "Option: 'Destination directory', expected enabled stated: '$runtimeLibs', actual: '$destDirEnabled'" }
    }
  }
}

// TODO: remove it after GUI-59 fixing
fun KotlinGuiTestCase.dialogWithoutClosing(title: String? = null,
                                           ignoreCaseTitle: Boolean = false,
                                           timeout: Long = defaultTimeout,
                                           func: JDialogFixture.() -> Unit) {
  val dialog = dialog(title, ignoreCaseTitle, timeout)
  func(dialog)
}

fun KotlinGuiTestCase.saveAndCloseCurrentEditor() {
  ideFrame {
    editor {
      logTestStep("Going to save and close currently opened file")
      shortcut(Modifier.CONTROL + Key.S)
      shortcut(Modifier.CONTROL + Key.F4)
    }
  }
}

fun KotlinGuiTestCase.testCreateGradleAndConfigureKotlin(
  kotlinKind: KotlinKind,
  kotlinVersion: String,
  project: ProjectProperties,
  expectedFacet: FacetStructure,
  gradleOptions: NewProjectDialogModel.GradleProjectOptions) {
  if (!isIdeFrameRun()) return
  val extraTimeOut = 4000L
  createGradleProject(
    projectPath = projectFolder,
    gradleOptions = gradleOptions)
  waitAMoment(extraTimeOut)
  when (kotlinKind) {
    KotlinKind.JVM -> configureKotlinJvmFromGradle(kotlinVersion)
    KotlinKind.JS -> configureKotlinJsFromGradle(kotlinVersion)
    else -> throw IllegalStateException("Cannot configure to Kotlin/Common kind.")
  }
  waitAMoment(extraTimeOut)
  saveAndCloseCurrentEditor()
  editSettingsGradle()
  editBuildGradle(
    kotlinVersion = kotlinVersion,
    isKotlinDslUsed = gradleOptions.useKotlinDsl
  )
  waitAMoment(extraTimeOut)
  gradleReimport()
  waitAMoment(extraTimeOut)

  projectStructureDialogScenarios.checkGradleExplicitModuleGroups(
    project = project,
    kotlinVersion = kotlinVersion,
    projectName = gradleOptions.artifact,
    expectedFacet = expectedFacet
  )
}

fun ProjectStructureDialogScenarios.checkGradleExplicitModuleGroups(
  project: ProjectProperties,
  kotlinVersion: String,
  projectName: String,
  expectedFacet: FacetStructure
) {
  openProjectStructureAndCheck {
    projectStructureDialogModel.checkLibrariesFromMavenGradle(
      buildSystem = BuildSystem.Gradle,
      kotlinVersion = kotlinVersion,
      expectedJars = project.jars.getJars(kotlinVersion)
    )
    projectStructureDialogModel.checkFacetInOneModule(
      expectedFacet,
      "$projectName", "${projectName}_main", "Kotlin"
    )
    projectStructureDialogModel.checkFacetInOneModule(
      expectedFacet,
      "$projectName", "${projectName}_test", "Kotlin"
    )
  }
}


fun KotlinGuiTestCase.createKotlinMPProject(
  projectPath: String,
  moduleName: String,
  mppProjectStructure: NewProjectDialogModel.MppProjectStructure,
  setOfMPPModules: Set<KotlinKind>
) {
  assert(setOfMPPModules.contains(KotlinKind.Common)) { "At least common MPP module should be specified" }
  logTestStep("Create new MPP project with modules $setOfMPPModules")
  welcomePageDialogModel.createNewProject()
  newProjectDialogModel.assertGroupPresent(NewProjectDialogModel.Groups.Kotlin)
  newProjectDialogModel.createKotlinMPProject(
    projectPath = projectPath,
    moduleName = moduleName,
    mppProjectStructure = mppProjectStructure,
    isJvmIncluded = setOfMPPModules.contains(KotlinKind.JVM),
    isJsIncluded = setOfMPPModules.contains(KotlinKind.JS)
  )
}