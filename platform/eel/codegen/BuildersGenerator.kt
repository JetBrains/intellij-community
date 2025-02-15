// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.codegen

import com.intellij.analysis.AnalysisBundle
import com.intellij.analysis.AnalysisScope
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.actions.OptimizeImportsProcessor
import com.intellij.codeInsight.actions.RearrangeCodeProcessor
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ex.GlobalInspectionContextBase
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.facet.FacetManager
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.application.*
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.modules
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.readText
import com.intellij.openapi.vfs.writeText
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.findParentOfType
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.closeProjectAsync
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.registerExtension
import com.intellij.util.io.copyRecursively
import com.intellij.workspaceModel.ide.legacyBridge.WorkspaceFacetContributor
import com.maddyhome.idea.copyright.actions.UpdateCopyrightAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModuleDependency
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.idea.workspaceModel.KotlinFacetContributor
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import kotlin.collections.ArrayDeque
import kotlin.collections.addAll
import kotlin.io.path.pathString
import kotlin.jvm.optionals.getOrNull

/**
 * This test generates builders for `com.intellij.platform.eel.EelApi`.
 *
 * If you want to regenerate some builders, run the test.
 * No specific arguments or prerequisites are required.
 * Remember to commit new builders.
 */
@TestApplication
class BuildersGenerator {
  companion object {
    private var oldInitInspections = false

    @BeforeAll
    @JvmStatic
    fun beforeAll() {
      oldInitInspections = InspectionProfileImpl.INIT_INSPECTIONS
      InspectionProfileImpl.INIT_INSPECTIONS = true
    }

    @AfterAll
    @JvmStatic
    fun afterAll() {
      InspectionProfileImpl.INIT_INSPECTIONS = oldInitInspections
    }
  }

  val tempProject = projectFixture(openAfterCreation = false)

  @Test
  fun `generate sources and check changes`(): Unit = runBlocking(Dispatchers.Default) {
    val moduleName = "intellij.platform.eel"
    var tempProject = tempProject.get()
    try {
      ApplicationManager.getApplication().registerExtension(
        WorkspaceFacetContributor.EP_NAME,
        KotlinFacetContributor(),
        tempProject,
      )

      val thisProject = Path.of(PathManager.getHomePath())

      run {
        val tempProjectPath = Path.of(tempProject.basePath!!)
        Files.createDirectories(tempProjectPath.resolve(".idea"))
        for (subpath in listOf(".idea/copyright", ".idea/scopes", ".idea/codeStyleSettings.xml")) {
          thisProject.resolve(subpath).copyRecursively(tempProjectPath.resolve(subpath))
        }
      }

      tempProject = ProjectManagerEx.getInstanceEx().openProjectAsync(Path.of(tempProject.basePath!!))!!

      val (newEelModule: Module, genSrcDirName: Path) = createModuleMirror(tempProject, moduleName)

      synchronizeVariousCaches(tempProject)

      // The value is the content before any change.
      val initialFileContent: Map<VirtualFile, Optional<String>> = fillRequests(tempProject, newEelModule, genSrcDirName)

      for (virtualFile in initialFileContent.keys) {
        prettifyFile(tempProject, virtualFile)
      }

      synchronizeVariousCaches(tempProject)

      // Restoring and reformatting files to check for changes not using git.
      val newContent: Map<VirtualFile, String> = readAction {
        initialFileContent.mapValues { (virtualFile, _) ->
          virtualFile.readText()
        }
      }

      for ((virtualFile, initialContent) in initialFileContent) {
        if (initialContent.isPresent) {
          writeAction {
            virtualFile.writeText(initialContent.get())
          }
          prettifyFile(tempProject, virtualFile)
        }
      }

      synchronizeVariousCaches(tempProject)

      val initialPrettifiedContent: Map<VirtualFile, Optional<String>> = initialFileContent.mapValues { (virtualFile, initialContent) ->
        if (initialContent.isEmpty) {
          Optional.empty()
        }
        else {
          Optional.of(readAction { virtualFile.readText() })
        }
      }

      for ((virtualFile, content) in newContent.entries) {
        writeAction {
          virtualFile.writeText(content)
        }
      }

      checkImportantChanges(thisProject, initialPrettifiedContent, newContent)

      checkUnimportantChanges(thisProject, initialFileContent, newContent)
    }
    finally {
      tempProject.closeProjectAsync(save = false)
    }
  }

  private suspend fun synchronizeVariousCaches(tempProject: Project) {
    writeAction {
      FileDocumentManager.getInstance().saveAllDocuments()
      VirtualFileManager.getInstance().syncRefresh()
    }
    SaveAndSyncHandler.getInstance().refreshOpenFiles()
    IndexingTestUtil.suspendUntilIndexesAreReady(tempProject)
  }

  private fun checkImportantChanges(
    thisProject: Path,
    initialPrettifiedContent: Map<VirtualFile, Optional<String>>,
    newContent: Map<VirtualFile, String>,
  ) {
    val importantChanges = initialPrettifiedContent.keys
      .plus(newContent.keys)
      .mapNotNull { virtualFile ->
        val oldPrettifiedContent = initialPrettifiedContent[virtualFile]?.getOrNull()
        val newContent = newContent[virtualFile]
        when {
          oldPrettifiedContent == newContent -> null
          oldPrettifiedContent == null -> "added: ${thisProject.relativize(virtualFile.toNioPath())}"
          newContent == null -> "deleted: ${thisProject.relativize(virtualFile.toNioPath())}"
          else -> "changed: ${thisProject.relativize(virtualFile.toNioPath())}"
        }
      }

    if (importantChanges.isNotEmpty()) {
      throw AssertionError("""
        |Some builder interfaces changed, but no new builders were generated.
        |Changes have been written to the disk, don't forget to commit them.
        |
        |${importantChanges.joinToString("\n")}
      """.trimMargin())
    }
  }

  private fun checkUnimportantChanges(
    thisProject: Path,
    initialContent: Map<VirtualFile, Optional<String>>,
    newContent: Map<VirtualFile, String>,
  ) {
    val unimportantChanges = initialContent.mapNotNull { (virtualFile, oldContent) ->
      val oldContent = oldContent.get()
      val newContent = newContent[virtualFile]!!
      if (oldContent != newContent)
        "changed: ${thisProject.relativize(virtualFile.toNioPath())}"
      else
        null
    }

    if (unimportantChanges.isNotEmpty()) {
      logger<BuildersGenerator>().warn(
        """
        |Some styling options in the project has changed. It is recommended to commit new generated builders.
        |
        |${unimportantChanges.joinToString("\n")}
        """.trimMargin()
      )
    }
  }

  private suspend fun prettifyFile(tempProject: Project, virtualFile: VirtualFile) {
    val inspectionManager = InspectionManager.getInstance(tempProject)
    val globalContext = inspectionManager.createNewGlobalContext() as GlobalInspectionContextBase
    val scope = AnalysisScope(readAction {
      PsiManager.getInstance(tempProject).findFile(virtualFile)!!
    })

    globalContext.codeCleanup(
      scope,
      InspectionProjectProfileManager.getInstance(tempProject).currentProfile,
      AnalysisBundle.message("cleanup.in.file"),
      null,
      false,
    )

    writeCommandAction(tempProject, "reformat") {
      val psiFile = PsiManager.getInstance(tempProject).findFile(virtualFile)!!
      OptimizeImportsProcessor(tempProject, psiFile).run()

      RearrangeCodeProcessor(psiFile).run()

      CodeInsightSettings.getInstance().ENABLE_SECOND_REFORMAT = false
      CodeStyleManager.getInstance(tempProject).reformat(psiFile)
      CodeInsightSettings.getInstance().ENABLE_SECOND_REFORMAT = true
      CodeStyleManager.getInstance(tempProject).reformat(psiFile)

      ProgressManager.getInstance().run(
        UpdateCopyrightAction.UpdateCopyrightTask(tempProject, AnalysisScope(psiFile), true, PerformInBackgroundOption.DEAF))
    }
  }

  private suspend fun createModuleMirror(
    tempProject: Project,
    moduleName: String,
  ): Pair<Module, Path> {
    var genSrcDirName: Path? = null
    val ultimateProject: JpsProject = IntelliJProjectConfiguration.loadIntelliJProject(Path.of(PathManager.getHomePath()).pathString)

    val kotlinLibraries = ultimateProject.libraryCollection.libraries.filter {
      it.name == "kotlin-stdlib" || it.name == "kotlinx-coroutines-core"
    }

    val newEelModule: Module = writeAction {
      val projectModel = ModuleManager.getInstance(tempProject).getModifiableModel()

      val jpsModuleQueue = ArrayDeque(listOf(
        ultimateProject.modules.first { it.name == moduleName }
      ))
      // TODO Dependencies don't work well. Kotlin can't resolve types from them.
      run {
        var i = 0
        while (i < jpsModuleQueue.size) {
          val jpsModule = jpsModuleQueue[i]
          for (dependencyElement in jpsModule.dependenciesList.dependencies) {
            if (dependencyElement is JpsModuleDependency) {
              jpsModuleQueue.addLast(dependencyElement.module!!)
            }
          }
          ++i
        }
      }

      val platformLibs = kotlinLibraries.map { kotlinLib ->
        val lib = LibraryTablesRegistrar.getInstance().getLibraryTable(tempProject).createLibrary(kotlinLib.name)
        val libModel = lib.modifiableModel
        for (root in kotlinLib.getRoots(JpsOrderRootType.COMPILED)) {
          libModel.addRoot(root.url, OrderRootType.CLASSES)
        }
        libModel.commit()
        lib
      }

      while (true) {
        val jpsModule = jpsModuleQueue.removeLastOrNull() ?: break
        val module = projectModel.newModule(jpsModule.name, StdModuleTypes.JAVA.id)

        val rootModel = module.rootManager.modifiableModel

        rootModel.addLibraryEntries(platformLibs, DependencyScope.COMPILE, false)

        for (sourceRoot in jpsModule.sourceRoots) {
          when (val properties = sourceRoot.properties) {
            is JavaSourceRootProperties -> {
              // TODO Filter only src roots.
              if (properties.isForGeneratedSources) {
                check(genSrcDirName == null) { "Multiple gen src dirs: $genSrcDirName, ${sourceRoot.path}" }
                genSrcDirName = sourceRoot.path
              }
              rootModel.addContentEntry(sourceRoot.url).addSourceFolder(sourceRoot.url, false)
            }
          }
        }
        rootModel.commit()
      }

      projectModel.commit()

      tempProject.modules.first { it.name == moduleName }
    }

    check(genSrcDirName != null) { "No gen src dir found" }

    writeAction {
      for (module in tempProject.modules) {
        val facetManager = FacetManager.getInstance(module)
        val facetModel = facetManager.createModifiableModel()
        facetModel.addFacet(facetManager.createFacet(KotlinFacetType.INSTANCE, "Kotlin", null))
        facetModel.commit()
      }
    }
    return Pair(newEelModule, genSrcDirName)
  }
}

private suspend fun fillRequests(
  tempProject: Project,
  newEelModule: Module,
  genSrcDirName: Path,
): Map<VirtualFile, Optional<String>> {
  val methods = mutableListOf<BuilderRequest>()

  val queue = ArrayDeque<VirtualFile>()
  queue.addAll(newEelModule.rootManager.contentRoots)
  while (true) {
    val virtualFile = queue.removeFirstOrNull() ?: break
    if (virtualFile.isDirectory) {
      if (virtualFile.toNioPath() != genSrcDirName) {
        queue.addAll(virtualFile.children)
      }
    }
    else if (virtualFile.extension == "kt") {
      val document = withContext(Dispatchers.EDT) {
        FileDocumentManager.getInstance().getDocument(virtualFile, tempProject)!!
      }
      readAction {
        val psiFile = PsiDocumentManager.getInstance(tempProject).getPsiFile(document)!!
        findBuilders(psiFile, methods)
      }
    }
  }

  return writeBuilderFiles(methods, tempProject, genSrcDirName)
}

private class BuilderRequest(
  val argsInterfaceFqn: String,
  val clsFqn: String,
  val methodName: String,
  val methodKDoc: String,
  val returnTypeFqn: String,
  val imports: Set<String>,
)

private fun findBuilders(psiFile: PsiFile, methods: MutableList<BuilderRequest>) {
  val imports = mutableSetOf<String>()
  psiFile.acceptChildren(object : PsiRecursiveElementWalkingVisitor() {
    override fun visitElement(element: PsiElement) {
      when {
        element is KtImportDirective -> {
          imports += element.text
        }
        element is KtAnnotationEntry && element.typeReference?.renderWithFqnTypes() == "com.intellij.platform.eel.GeneratedBuilder" -> {
          val valueParameter = element.parent.parent as? KtParameter ?: return
          val typeFqn = analyze(valueParameter) {
            valueParameter.typeReference?.type?.toString()?.replace("/", ".") ?: return
          }
          val fn = valueParameter.parent.parent as? KtNamedFunction ?: return
          val methodName = fn.name ?: return
          val methodCls = valueParameter.containingClass()?.fqName ?: return

          methods += BuilderRequest(
            argsInterfaceFqn = typeFqn,
            clsFqn = methodCls.asString(),
            methodName = methodName,
            methodKDoc = fn.docComment?.extractText() ?: "",
            returnTypeFqn = fn.typeReference!!.renderWithFqnTypes(),
            imports = imports,
          )
        }
        else -> {
          super.visitElement(element)
        }
      }
    }
  })
}

private suspend fun writeBuilderFiles(
  methods: MutableList<BuilderRequest>,
  tempProject: Project,
  genSrcDirName: Path,
): LinkedHashMap<VirtualFile, Optional<String>> {
  val createdVirtualFiles = linkedMapOf<VirtualFile, Optional<String>>()

  methods.sortBy { listOf(it.argsInterfaceFqn, it.clsFqn, it.methodName).joinToString() }
  val imports = methods
    .flatMapTo(sortedSetOf()) { listOf(it.argsInterfaceFqn, it.clsFqn) }
    .plus(methods.flatMapTo(sortedSetOf()) { request -> request.imports.map { import -> import.removePrefix("import ") } })
    .toHashSet()

  for ((argsInterfaceFqn, builderRequests) in methods.groupByTo(linkedMapOf()) { it.argsInterfaceFqn }) {
    lateinit var packageFqn: String
    lateinit var fileName: String

    val builderRequests = builderRequests.distinctBy { listOf(it.clsFqn, it.methodName, it.argsInterfaceFqn) }

    @Language("kotlin")
    var text = ""

    readAction {
      val argsInterface = getKtClassFromKtLightClass(
        JavaPsiFacade.getInstance(tempProject).findClass(argsInterfaceFqn, GlobalSearchScope.projectScope(tempProject)))
      check(argsInterface != null) { "PsiClass for $argsInterfaceFqn not found" }
      val argsInterfaceParentCls =
        argsInterface.findParentOfType<KtClass>()
        ?: error("${argsInterface.name} is not a nested class")

      packageFqn = argsInterfaceParentCls.fqName!!.toString().substringBeforeLast('.')

      fileName = "${argsInterface.name}Builder.kt"

      val requiredArguments: List<RequiredArgument> = argsInterface.getProperties()
        .filter { property -> !property.hasBody() }
        .map { property ->
          RequiredArgument(
            name = property.name!!,
            typeFqn = property.typeReference!!.getFqn()!!,
            kdoc = property.docComment?.extractText() ?: "",
          )
        }
        .sortedBy { it.name }

      val optionalArguments: List<OptionalArgument> = argsInterface.getProperties()
        .filter { property -> property.hasBody() }
        .map { property ->
          OptionalArgument(
            name = property.name!!,
            typeFqn = property.typeReference!!.getFqn()!!,
            body = property.getter!!.bodyExpression!!.renderWithFqnTypes(),
            kdoc = property.docComment?.extractText() ?: "",
          )
        }
        .sortedBy { it.name }

      for (fullTypeFqn in requiredArguments.map { it.typeFqn } + optionalArguments.map { it.typeFqn }) {
        for (singleTypeFqn in fullTypeFqn.split(Regex("[<>,?]"))) {
          imports += singleTypeFqn.trim()
        }
      }

      val propertyNames = argsInterface.getProperties().map { property -> property.name!! }.sortedBy { it }

      for (builderRequest in builderRequests) {
        val ownedBuilderName = "${builderRequest.clsFqn.replace('.', '_')}_${builderRequest.methodName}_OwnedBuilder"

        val kdoc = buildString {
          append(builderRequest.methodKDoc)
          append("\n")
          for (prop in requiredArguments) {
            val kdoc = prop.kdoc.takeIf(String::isNotEmpty) ?: continue
            append("\n@param ${prop.name} ${kdoc.prependIndent(" ").trim()}")
          }
        }

        text += """
        ${kdoc.renderKdoc()}@GeneratedBuilder.Result
        fun ${builderRequest.clsFqn}.${builderRequest.methodName}(${
          requiredArguments.joinToString("") { prop ->
            "\n${prop.name}: ${prop.typeFqn},"
          }.surroundWithNewlinesIfNotBlank()
        }): $ownedBuilderName =
          $ownedBuilderName(
            owner = this,${requiredArguments.joinToString("") { prop -> "\n${prop.name} = ${prop.name}," }}
          )

        @GeneratedBuilder.Result
        class $ownedBuilderName(
          private val owner: ${builderRequest.clsFqn}, ${
          requiredArguments.joinToString("") { prop ->
            "\nprivate var ${prop.name}: ${prop.typeFqn},"
          }
        }
        ) : com.intellij.platform.eel.OwnedBuilder<${builderRequest.returnTypeFqn}> {
        ${
          optionalArguments.joinToString("\n\n") { prop ->
            "private var ${prop.name}: ${prop.typeFqn} = ${prop.body}"
          }
        }
        ${
          propertyNames.joinToString("\n") { name ->
            renderPropertyInBuilder(tempProject, name, ownedBuilderName, requiredArguments, optionalArguments)
          }
        }

          /**
          * Complete the builder and call [${builderRequest.clsFqn}.${builderRequest.methodName}] 
          * with an instance of [${builderRequest.argsInterfaceFqn}].
          */
          @org.jetbrains.annotations.CheckReturnValue
          override suspend fun eelIt(): ${builderRequest.returnTypeFqn} =
            owner.${builderRequest.methodName}(${argsInterface.name}Impl(
            ${propertyNames.joinToString("\n") { name -> "$name = $name," }}
            ))
        }
        """
      }

      text += """
      @GeneratedBuilder.Result
      class ${argsInterface.name}Builder(
      ${
        requiredArguments.joinToString("\n") { prop ->
          "${prop.kdoc.renderKdoc()}private var ${prop.name}: ${prop.typeFqn},"
        }
      }
      ) {
      ${
        optionalArguments.joinToString("\n\n") { prop ->
          "private var ${prop.name}: ${prop.typeFqn} = ${prop.body}"
        }
      }
      ${
        propertyNames.joinToString("\n") { name ->
          renderPropertyInBuilder(tempProject, name, "${argsInterface.name}Builder", requiredArguments, optionalArguments)
        }
      }

        fun build(): ${argsInterface.name} =
          ${argsInterface.name}Impl(
          ${propertyNames.joinToString("\n") { name -> "$name = $name," }}
          )
      }

      @GeneratedBuilder.Result
      private class ${argsInterface.name}Impl(
      ${
        propertyNames.joinToString("\n") { name ->
          val typeFqn =
            requiredArguments.firstOrNull { it.name == name }?.typeFqn ?: optionalArguments.first { it.name == name }.typeFqn
          "override val $name: $typeFqn,"
        }
      }
      ) : $argsInterfaceFqn
      """
    }

    text = """
      /**
       * This file is generated by [${BuildersGenerator::class.java.name}].
       */
      package $packageFqn
      
      ${imports.joinToString("\n") { "import $it" }}
      
      """.trimIndent() + text

    writeAction {
      var virtualDir = VirtualFileManager.getInstance().findFileByNioPath(genSrcDirName)!!
      for (dirName in packageFqn.split('.')) {
        var child = virtualDir.findChild(dirName)
        if (child != null && !child.isDirectory) {
          child.delete(null)
          child = null
        }
        if (child == null) {
          child = virtualDir.createChildDirectory(null, dirName)
        }
        virtualDir = child
      }
      var virtualFile = virtualDir.findChild(fileName)
      val initialText: Optional<String> =
        if (virtualFile != null) {
          Optional.of(virtualFile.readText())
        }
        else {
          virtualFile = virtualDir.createChildData(null, fileName)
          Optional.empty()
        }

      // TODO Don't rewrite files.
      virtualFile.writeText(text)

      createdVirtualFiles[virtualFile] = initialText
    }
  }

  return createdVirtualFiles
}

private fun renderPropertyInBuilder(
  tempProject: Project,
  name: String,
  builderName: String,
  requiredArguments: List<RequiredArgument>,
  optionalArguments: List<OptionalArgument>,
): String = buildString {
  val typeFqn =
    requiredArguments.firstOrNull { it.name == name }?.typeFqn ?: optionalArguments.first { it.name == name }.typeFqn
  val kdoc =
    requiredArguments.firstOrNull { it.name == name }?.kdoc ?: optionalArguments.first { it.name == name }.kdoc

  append("""
    ${kdoc.renderKdoc()}fun $name(arg: $typeFqn): $builderName = apply {
      this.$name = arg
    }""")

  if (typeFqn.startsWith("kotlin.collections.List<")) {
    append("""
      ${kdoc.renderKdoc()}fun $name(vararg arg: ${typeFqn.run { substring(24, length - 1) }}): $builderName = apply {
        this.$name = listOf(*arg)
      }""")
  }

  val psiClass = JavaPsiFacade.getInstance(tempProject).findClass(typeFqn, GlobalSearchScope.allScope(tempProject))

  if (psiClass?.isEnum == true) {
    val fields = psiClass.allFields.associateByTo(sortedMapOf()) { field ->
      Regex("(?:^|_)[a-z0-9]+").findAll(field.name.lowercase())
        .joinToString("") { match ->
          match.value.trimStart('_').replaceFirstChar(Char::uppercase)
        }
        .replaceFirstChar(Char::lowercase)
    }

    for ((enumMethodName, field) in fields) {
      append("""
        ${field.docComment?.text.orEmpty()}fun $enumMethodName(): $builderName =
          $name($typeFqn.${field.name})""")
    }
  }
}

private fun PsiElement.renderWithFqnTypes(): String = buildString {
  accept(object : PsiRecursiveElementWalkingVisitor() {
    override fun visitElement(element: PsiElement) {
      var written = false
      when (element) {
        is KtTypeReference -> {
          val fqn = element.getFqn()
          if (fqn != null) {
            append(fqn.javaToKotlinTypes())
            written = true
          }
        }
      }
      if (!written && element.firstChild == null) {
        append(element.text)
      }
      if (!written) {
        super.visitElement(element)
      }
    }
  })
}

private fun KDoc.extractText(): String =
  text
    .trimIndent()
    .removePrefix("/**")
    .removeSuffix("*/")
    .trim()
    .lines()
    .joinToString("\n") { line -> line.replace(Regex("^\\s*\\* ?"), "").trimEnd() }

private fun String.renderKdoc(): String =
  if (isBlank()) {
    ""
  }
  else {
    // `prependIndent` doesn't indent empty lines, hence it's not used here.
    "/**\n${trim().lines().joinToString("\n") { line -> " * $line" }}\n */"
  }

private fun String.surroundWithNewlinesIfNotBlank(): String =
  if (isBlank()) ""
  else "\n${trim()}\n"

private fun KtTypeReference.getFqn(): String? =
  analyze(this) { this@getFqn.type }.toString().replace("/", ".")

private fun getKtClassFromKtLightClass(lightClass: PsiClass?): KtClass? {
  return when (val originalElement = (lightClass as? KtLightClass)?.kotlinOrigin) {
    is KtClass -> originalElement
    else -> null
  }
}

private fun String.javaToKotlinTypes(): String =
  replace("java.lang.String", "String")
    .replace("java.lang.Integer", "Int")
    .replace("java.lang.Boolean", "Boolean")
    .replace("java.lang.Float", "Float")
    .replace("java.lang.Double", "Double")
    .replace("java.lang.Long", "Long")
    .replace("java.lang.Object", "Any")
    .replace("java.util.List", "List")
    .replace("java.util.Map", "Map")

private class RequiredArgument(val name: String, val typeFqn: String, val kdoc: String)
private class OptionalArgument(val name: String, val typeFqn: String, val kdoc: String, val body: String)