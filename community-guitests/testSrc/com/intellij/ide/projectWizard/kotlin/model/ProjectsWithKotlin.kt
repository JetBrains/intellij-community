// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.model

import com.intellij.openapi.application.PathManager
import com.intellij.testGuiFramework.fixtures.JDialogFixture
import com.intellij.testGuiFramework.framework.GuiTestUtil.fileInsertFromBegin
import com.intellij.testGuiFramework.framework.Timeouts.defaultTimeout
import com.intellij.testGuiFramework.framework.GuiTestUtil.fileSearchAndReplace
import com.intellij.testGuiFramework.framework.GuiTestUtil.isFileContainsLine
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.*
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.waitUntil
import com.intellij.testGuiFramework.util.*
import com.intellij.testGuiFramework.util.scenarios.*
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.GradleGroupModules.ExplicitModuleGroups
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel.GradleGroupModules.QualifiedNames
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.timing.Pause
import org.fest.swing.timing.Timeout
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import org.hamcrest.core.Is.`is` as Matcher_Is

/**
 * Creates a Java project with a specified framework
 * @param projectPath full path where the new project should be created
 * last item in the path is considered as a new project name
 * @param framework framework name, if empty - no framework should be selected
 */
fun KotlinGuiTestCase.createJavaProject(
  projectPath: String,
  projectSdk: String,
  framework: LibrariesSet = emptySet()) {
  welcomePageDialogModel.createNewProject()
  newProjectDialogModel.assertGroupPresent(NewProjectDialogModel.Groups.Kotlin)
  newProjectDialogModel.createJavaProject(projectPath, projectSdk, framework)
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
  gradleOptions: NewProjectDialogModel.GradleProjectOptions,
  projectSdk: String
) {
  welcomePageDialogModel.createNewProject()
  newProjectDialogModel.assertGroupPresent(NewProjectDialogModel.Groups.Kotlin)
  newProjectDialogModel.createGradleProject(projectPath, gradleOptions, projectSdk)
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
  projectSdk: String,
  archetype: String = "",
  kotlinVersion: String = ""
) {
  welcomePageDialogModel.createNewProject()
  newProjectDialogModel.assertGroupPresent(NewProjectDialogModel.Groups.Kotlin)

  val mavenOptions = NewProjectDialogModel.MavenProjectOptions(
    artifact = artifact,
    useArchetype = archetype.isNotEmpty(),
    archetypeGroup = "org.jetbrains.kotlin:$archetype",
    archetypeVersion = "$archetype:$kotlinVersion"
  )
  newProjectDialogModel.createMavenProject(projectPath, mavenOptions, projectSdk)
}

/**
 * Creates a KOtlin project with a specified framework
 * @param projectPath full path where the new project should be created
 * last item in the path is considered as a new project name
 * @param project properties of the created project
 */
fun KotlinGuiTestCase.createKotlinProject(
  projectPath: String,
  project: ProjectProperties) {
  welcomePageDialogModel.createNewProject()
  newProjectDialogModel.assertGroupPresent(NewProjectDialogModel.Groups.Kotlin)
  newProjectDialogModel.createKotlinProject(projectPath, project.frameworkName)
}

/**
 * Configure Kotlin JVM in a java project
 * @param libInPlugin
 *     - true - the kotlin specific jar files are taken from plugin
 *     - false - needed jar files are created in the `lib` folder within the project folder
 * */
fun KotlinGuiTestCase.configureKotlinJvm(libInPlugin: Boolean) {
  ideFrame {
    waitAMoment()
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
  var result = false
  val maxAttempts = 3
  ideFrame {
    var counter = 0
    do {
      try {
        logTestStep("$logText. Attempt #${counter + 1}")
        waitAMoment()
        invokeMainMenu(menuTitle)
        val dlg  = dialog("", true, timeout = Timeouts.seconds05, predicate = {_, _ -> true})
        logUIStep("Found dialog: ${dlg.target().title}")
        if(dlg.target().title != dialogTitle)
          dlg.apply {
            Pause.pause(1000)
            button("Cancel").click()
          }
        else
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
    waitUntil("Wait for button OK is enabled") { button("OK").isEnabled }
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
 * @param expectedLibName expected name of library shown in the Project Structure
 * */
fun ProjectStructureDialogScenarios.checkKotlinLibsInStructureFromProject(
  projectPath: String,
  expectedLibName: String) {
  val expectedJars = getKotlinLibInProject(projectPath)
    .map { projectPath + File.separator + "lib" + File.separator + it }
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
 * @param project tested project properties
 * @param kotlinVersion tested kotlin version (used for searching correct set of jars)
 * */
fun ProjectStructureDialogScenarios.checkKotlinLibsInStructureFromPlugin(
  project: ProjectProperties,
  kotlinVersion: String) {
  val expectedLibName = project.libName!!
  val configPath = PathManager.getConfigPath().normalizeSeparator()
  val expectedJars = project
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
 * @param expectedLibs set of expected jar files added to the project folder
 * */
fun KotlinGuiTestCase.checkKotlinLibInProject(projectPath: String,
                                              expectedLibs: List<String>) {
  val actualLibs = getKotlinLibInProject(projectPath)

  expectedLibs.forEach {
    logInfo("check if expected '$it' is present")
    assert(actualLibs.contains(it)) { "Expected, but absent file: $it" }
  }

  actualLibs.forEach {
    logInfo("check if existing '$it' is expected")
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

fun KotlinGuiTestCase.addDevRepositoryToBuildGradle(fileName: Path, isKotlinDslUsed: Boolean) {
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

fun KotlinGuiTestCase.addDevRepositoryToPomXml(fileName: Path) {
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

fun KotlinGuiTestCase.changeKotlinVersionInBuildGradle(fileName: Path,
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

fun KotlinGuiTestCase.changeKotlinVersionInPomXml(fileName: Path, kotlinVersion: String) {
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
  val fileName = Paths.get(projectFolder, "settings.gradle")
  if (KotlinTestProperties.isArtifactOnlyInDevRep) {
    if(isFileContainsLine(fileName, "repositories"))
      addDevRepositoryToBuildGradle(fileName, isKotlinDslUsed = false)
    else {
      val pluginManagement = "pluginManagement"
      val repositoriesLines = listOf(
        "repositories {",
          "mavenCentral()",
          "maven { url 'https://dl.bintray.com/kotlin/kotlin-dev' }",
        "}"
      )

      if(isFileContainsLine(fileName, pluginManagement)){
        println("editSettingsGradle: file '$fileName' contains `$pluginManagement`")
        fileSearchAndReplace(fileName){
          if(it.contains(pluginManagement))
            listOf(it, *repositoriesLines.toTypedArray()).joinToString(separator = "\n")
          else it
        }
      }
      else{
        println("editSettingsGradle: file '$fileName' does NOT contain `$pluginManagement`")
        fileInsertFromBegin(fileName, listOf(
          "pluginManagement {",
          *repositoriesLines.toTypedArray(),
          "}"
        ))
      }
    }
  }
}

fun KotlinGuiTestCase.editBuildGradle(
  kotlinVersion: String,
  isKotlinDslUsed: Boolean = false,
  vararg projectName: String = emptyArray()) {
  //   if project is configured to old Kotlin version, it must be released and no changes are required in the build.gradle file
  if (!KotlinTestProperties.isActualKotlinUsed()) return

  val fileName = Paths.get(projectFolder, *projectName , "build.gradle${if (isKotlinDslUsed) ".kts" else ""}")
  logTestStep("Going to edit $fileName")

  if (KotlinTestProperties.isArtifactOnlyInDevRep) addDevRepositoryToBuildGradle(fileName, isKotlinDslUsed)
  if (!KotlinTestProperties.isArtifactPresentInConfigureDialog && KotlinTestProperties.kotlin_plugin_version_main != kotlinVersion)
    changeKotlinVersionInBuildGradle(fileName, isKotlinDslUsed, kotlinVersion)
}

fun KotlinGuiTestCase.editPomXml(kotlinVersion: String,
                                 vararg projectName: String = emptyArray()) {
  //   if project is configured to old Kotlin version, it must be released and no changes are required in the pom.xml file
  if (!KotlinTestProperties.isActualKotlinUsed()) return

  val fileName = Paths.get(projectFolder, *projectName, "pom.xml")
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
                                           timeout: Timeout = defaultTimeout,
                                           func: JDialogFixture.() -> Unit) {
  val dialog = dialog(title, ignoreCaseTitle, timeout = timeout)
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

fun KotlinGuiTestCase.createMavenAndConfigureKotlin(
  kotlinVersion: String,
  project: ProjectProperties,
  expectedFacet: FacetStructure
) {
  if (!isIdeFrameRun()) return
  val projectName = testMethod.methodName
  createMavenProject(
    projectPath = projectFolder,
    artifact = projectName,
    projectSdk = project.projectSdk)
  waitAMoment()
  when(project.modules.contains(TargetPlatform.JavaScript)){
    false -> configureKotlinJvmFromMaven(kotlinVersion)
    true -> configureKotlinJsFromMaven(kotlinVersion)
  }

  waitAMoment()
  saveAndCloseCurrentEditor()
  editPomXml(
    kotlinVersion = kotlinVersion
  )
  waitAMoment()
  mavenReimport()
  Pause.pause(5000)
  waitAMoment()
  mavenReimport()

  projectStructureDialogScenarios.openProjectStructureAndCheck {
    projectStructureDialogModel.checkLibrariesFromMavenGradle(
      buildSystem = BuildSystem.Maven,
      kotlinVersion = kotlinVersion,
      expectedJars = project.jars.getJars(kotlinVersion)
    )
    projectStructureDialogModel.checkFacetInOneModule(
      expectedFacet = expectedFacet,
      path = *arrayOf(projectName, "Kotlin")
    )
  }

  waitAMoment()
}


fun KotlinGuiTestCase.testCreateGradleAndConfigureKotlin(
  kotlinVersion: String,
  project: ProjectProperties,
  expectedFacet: FacetStructure,
  gradleOptions: NewProjectDialogModel.GradleProjectOptions) {
  if (!isIdeFrameRun()) return
  val projectName = testMethod.methodName
  createGradleProject(
    projectPath = projectFolder,
    gradleOptions = gradleOptions,
    projectSdk = project.projectSdk
  )
  waitAMoment()
  when {
    project.modules.contains(TargetPlatform.JVM16) || project.modules.contains(TargetPlatform.JVM18) -> configureKotlinJvmFromGradle(kotlinVersion)
    project.modules.contains(TargetPlatform.JavaScript) -> configureKotlinJsFromGradle(kotlinVersion)
    else -> throw IllegalStateException("Cannot configure to Common or Native kind.")
  }
  waitAMoment()
  waitForGradleReimport(projectName)
  saveAndCloseCurrentEditor()
  editSettingsGradle()
  editBuildGradle(
    kotlinVersion = kotlinVersion,
    isKotlinDslUsed = project.isKotlinDsl
  )
  waitAMoment()
  gradleReimport()
  assert(waitForGradleReimport(projectName)) { "Gradle import failed after editing of gradle files" }
  waitAMoment()

  projectStructureDialogScenarios.checkGradleFacets(
    project = project,
    kotlinVersion = kotlinVersion,
    expectedFacet = expectedFacet,
    gradleOptions = gradleOptions
  )
  waitAMoment()
}

fun ProjectStructureDialogScenarios.checkGradleFacets(
  project: ProjectProperties,
  kotlinVersion: String,
  expectedFacet: FacetStructure,
  gradleOptions: NewProjectDialogModel.GradleProjectOptions
) {
  openProjectStructureAndCheck {
    projectStructureDialogModel.checkLibrariesFromMavenGradle(
      buildSystem = project.buildSystem,
      kotlinVersion = kotlinVersion,
      expectedJars = project.jars.getJars(kotlinVersion)
    )
    val expectedFacets = when (gradleOptions.groupModules) {
      ExplicitModuleGroups -> {
        val explicitProjectName = gradleOptions.artifact
        mapOf(
          listOf(explicitProjectName, "${explicitProjectName}_main", "Kotlin") to expectedFacet,
          listOf(explicitProjectName, "${explicitProjectName}_test", "Kotlin") to expectedFacet
        )
      }
      QualifiedNames -> {
        val qualifiedProjectName = gradleOptions.artifact
        mapOf(
          listOf(qualifiedProjectName, "main", "Kotlin") to expectedFacet,
          listOf(qualifiedProjectName, "test", "Kotlin") to expectedFacet
        )
      }
    }
    for ((path, facet) in expectedFacets) {
      projectStructureDialogModel.checkFacetInOneModule(
        facet,
        path = *path.toTypedArray()
      )
    }
  }
}

fun KotlinGuiTestCase.createKotlinMPProject(
  projectPath: String,
  templateName: String,
  projectSdk: String
) {
  logTestStep("Create new $templateName project")
  welcomePageDialogModel.createNewProject()
  newProjectDialogModel.assertGroupPresent(NewProjectDialogModel.Groups.Kotlin)
  newProjectDialogModel.createKotlinMPProject(
    projectPath = projectPath,
    templateName = templateName,
    projectSdk = projectSdk
  )
}

fun KotlinGuiTestCase.testGradleProjectWithKotlin(
  kotlinVersion: String,
  project: ProjectProperties,
  expectedFacet: FacetStructure,
  gradleOptions: NewProjectDialogModel.GradleProjectOptions) {
  createGradleProject(
    projectPath = projectFolder,
    gradleOptions = gradleOptions,
    projectSdk = project.projectSdk
  )
  val projectName = testMethod.methodName
  waitAMoment()
  waitForGradleReimport(projectName)
  editSettingsGradle()
  editBuildGradle(
    kotlinVersion = kotlinVersion,
    isKotlinDslUsed = gradleOptions.useKotlinDsl
  )
  waitAMoment()
  gradleReimport()
  assert(waitForGradleReimport(projectName)) { "Gradle import failed after editing of gradle files" }
  waitAMoment()

  projectStructureDialogScenarios.checkGradleFacets(
    project, kotlinVersion, expectedFacet, gradleOptions
  )
  waitAMoment()
}
