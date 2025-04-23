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
import com.intellij.openapi.vfs.*
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.findParentOfType
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.registerExtension
import com.intellij.util.io.copyRecursively
import com.intellij.util.io.createDirectories
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
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.jvm.optionals.getOrNull

/**
 * This test generates builders for `com.intellij.platform.eel.EelApi`.
 *
 * If you want to regenerate some builders, run the test.
 * No specific arguments or prerequisites are required.
 * Remember to commit new builders.
 *
 * TODO The test fails with `Outdated stub in index` when it runs several times in a row.
 *   Remove the `system/test` directory in the monorepository.
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

      // The value is the pair of the old and the new content.
      val filesContent: Map<Path, Pair<Optional<String>, Optional<String>>> = fillRequests(tempProject, newEelModule, genSrcDirName)

      for ((path, contentPair) in filesContent) {
        if (contentPair.first.isPresent) {
          val virtualFile = VfsUtil.findFile(path, true)!!
          prettifyFile(tempProject, virtualFile)
        }
      }

      synchronizeVariousCaches(tempProject)

      val oldPrettifiedFiles = mutableMapOf<Path, String>()

      val unimportantChanges = filesContent.mapNotNull { (path, contentPair) ->
        val oldContent = contentPair.first.getOrNull()
        val newContent =
          if (contentPair.first.isPresent) {
            VfsUtil.findFile(path, true)?.run { readAction { readText() } }
          }
          else {
            null
          }
        if (newContent != null) {
          oldPrettifiedFiles[path] = newContent
        }
        if (oldContent != newContent)
          "changed: ${thisProject.relativize(path)}"
        else
          null
      }

      for ((path, contentPair) in filesContent) {
        path.parent.createDirectories()
        if (!path.exists()) Files.createFile(path)
        val virtualFile = VfsUtil.findFile(path, true)!!
        val (_, newContent) = contentPair
        writeAction {
          if (newContent.isPresent) {
            virtualFile.writeText(newContent.get())
          }
          else {
            virtualFile.delete(this)
          }
        }
      }

      for ((path, contentPair) in filesContent) {
        val virtualFile = VfsUtil.findFile(path, true)!!
        val (_, newContent) = contentPair
        if (newContent.isPresent) {
          prettifyFile(tempProject, virtualFile)
        }
      }

      synchronizeVariousCaches(tempProject)

      val importantChanges = filesContent.keys
        .mapNotNull { path ->
          val oldPrettifiedContent = oldPrettifiedFiles[path]
          val newContent = VfsUtil.findFile(path, true)?.run { readAction { readText() } }
          when {
            oldPrettifiedContent == newContent -> null
            oldPrettifiedContent == null -> "added: ${thisProject.relativize(path)}"
            newContent == null -> "deleted: ${thisProject.relativize(path)}"
            else -> "changed: ${thisProject.relativize(path)}"
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
    finally {
      withContext(Dispatchers.EDT) {
        ProjectManagerEx.getInstanceEx().forceCloseProject(tempProject, true)
      }
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

  private suspend fun prettifyFile(tempProject: Project, virtualFile: VirtualFile) {
    withContext(Dispatchers.EDT) {
      FileDocumentManager.getInstance().reloadFiles(virtualFile)
    }

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

      FileDocumentManager.getInstance().saveAllDocuments()
      virtualFile.writeText(virtualFile.readText().trimEnd())
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
): LinkedHashMap<Path, Pair<Optional<String>, Optional<String>>> {
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

@OptIn(ExperimentalPathApi::class)
private suspend fun writeBuilderFiles(
  methods: MutableList<BuilderRequest>,
  tempProject: Project,
  genSrcDirName: Path,
): LinkedHashMap<Path, Pair<Optional<String>, Optional<String>>> {
  val filesContent: LinkedHashMap<Path, Pair<Optional<String>, Optional<String>>> =
    genSrcDirName.walk().associateWithTo(linkedMapOf()) { path ->
      Optional.of(path.readText()) to Optional.empty()
    }

  methods.sortBy { listOf(it.argsInterfaceFqn, it.clsFqn, it.methodName).joinToString() }
  val imports = methods
    .flatMapTo(sortedSetOf()) { listOf(it.argsInterfaceFqn, it.clsFqn) }
    .plus(methods.flatMapTo(sortedSetOf()) { request -> request.imports.map { import -> import.removePrefix("import ") } })
    .toHashSet()

  val filesToWrite = mutableMapOf<Path, String>()

  for ((clsFqn, clsBuilderRequests) in methods.groupByTo(linkedMapOf()) { it.clsFqn }) {
    lateinit var sourcePackage: String

    @Language("kotlin")
    var text = ""

    readAction {
      val cls = getKtClassFromKtLightClass(
        JavaPsiFacade.getInstance(tempProject).findClass(clsFqn, GlobalSearchScope.projectScope(tempProject)))
      check(cls != null) { "PsiClass for ${clsFqn} not found" }
      check(cls.findParentOfType<KtClass>() == null) { "Nested classes are not supported: ${clsFqn}" }

      sourcePackage = clsFqn.substringBeforeLast('.')

      val argsInterfacesByFqn: Map<String, ArgInterfaceInfo> = clsBuilderRequests.associate { builderRequest ->
        val javaPsiFacade = JavaPsiFacade.getInstance(tempProject)
        val projectScope = GlobalSearchScope.projectScope(tempProject)
        val argsInterface = getKtClassFromKtLightClass(
          javaPsiFacade.findClass(builderRequest.argsInterfaceFqn, projectScope))
        check(argsInterface != null) { "PsiClass for ${builderRequest.argsInterfaceFqn} not found" }

        val argsAllInterfaces: Collection<KtClass> = run {
          val tree = hashSetOf<KtClass>()
          val queue = ArrayDeque(listOf(argsInterface))
          while (true) {
            val iface = queue.removeFirstOrNull() ?: break
            if (tree.add(iface)) {
              iface.getSuperTypeList()
                ?.entries
                ?.mapNotNull { superTypeListEntry -> superTypeListEntry.typeReference }
                ?.mapNotNull(KtTypeReference::getFqn)
                ?.map { fqn ->
                  val cls = getKtClassFromKtLightClass(javaPsiFacade.findClass(fqn, projectScope))
                  check(cls != null) { "PsiClass for $fqn not found" }
                  cls
                }
                ?.let(queue::addAll)
            }
          }
          tree
        }

        val requiredArguments: List<RequiredArgument> = argsAllInterfaces
          .flatMap { iface -> iface.getProperties() }
          .filter { property -> !property.hasBody() }
          .map { property ->
            RequiredArgument(
              name = property.name!!,
              typeFqn = property.typeReference!!.getFqn()!!,
              kdoc = property.docComment?.extractText() ?: "",
            )
          }
          .sortedBy { it.name }
          .distinct()

        val optionalArguments: List<OptionalArgument> = argsAllInterfaces
          .flatMap { iface -> iface.getProperties() }
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
          .distinct()

        val propertyNames = argsAllInterfaces
          .flatMap { iface -> iface.getProperties() }
          .map { property -> property.name!! }.sortedBy { it }

        for (fullTypeFqn in requiredArguments.map { it.typeFqn } + optionalArguments.map { it.typeFqn }) {
          for (singleTypeFqn in fullTypeFqn.split(Regex("[<>,?]"))) {
            imports += singleTypeFqn.trim()
          }
        }

        builderRequest.argsInterfaceFqn to ArgInterfaceInfo(
          name = argsInterface.name!!,
          requiredArguments = requiredArguments,
          optionalArguments = optionalArguments,
          propertyNames = propertyNames,
        )
      }

      val fileHeader = """
      /**
       * This file is generated by [${BuildersGenerator::class.java.name}].
       */
      package $sourcePackage
      
      ${imports.joinToString("\n") { "import $it" }}
      
      """

      for (builderRequest in clsBuilderRequests) {
        val ownedBuilderFqn = listOf(
          sourcePackage,
          builderRequest.clsFqn.removePrefix("$sourcePackage.") + "Helpers",
          builderRequest.methodName.replaceFirstChar(Char::uppercaseChar),
        ).joinToString(".")

        val requiredArguments = argsInterfacesByFqn[builderRequest.argsInterfaceFqn]!!.requiredArguments

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
        }): $ownedBuilderFqn =
          $ownedBuilderFqn(
            owner = this,${requiredArguments.joinToString("") { prop -> "\n${prop.name} = ${prop.name}," }}
          )
        """
      }

      text += "object ${clsFqn.removePrefix("$sourcePackage.").split('.').first()}Helpers {"

      for (builderRequest in clsBuilderRequests) {
        val argsInterfaceInfo = argsInterfacesByFqn[builderRequest.argsInterfaceFqn]!!

        text += """
        /**
         * Create it via [${builderRequest.clsFqn}.${builderRequest.methodName}]. 
         */
        @GeneratedBuilder.Result
        class ${builderRequest.methodName.replaceFirstChar(Char::uppercaseChar)}(
          private val owner: ${builderRequest.clsFqn}, ${
          argsInterfaceInfo.requiredArguments.joinToString("") { prop ->
            "\nprivate var ${prop.name}: ${prop.typeFqn},"
          }
        }
        ) : com.intellij.platform.eel.OwnedBuilder<${builderRequest.returnTypeFqn}> {
        ${
          argsInterfaceInfo.optionalArguments.joinToString("\n\n") { prop ->
            "private var ${prop.name}: ${prop.typeFqn} = ${prop.body}"
          }
        }
        ${
          argsInterfaceInfo.propertyNames.joinToString("\n") { name ->
            renderPropertyInBuilder(
              tempProject,
              name,
              builderRequest.methodName.replaceFirstChar(Char::uppercaseChar),
              argsInterfaceInfo.requiredArguments,
              argsInterfaceInfo.optionalArguments,
            )
          }
        }

          /**
          * Complete the builder and call [${builderRequest.clsFqn}.${builderRequest.methodName}] 
          * with an instance of [${builderRequest.argsInterfaceFqn}].
          */
          @org.jetbrains.annotations.CheckReturnValue
          override suspend fun eelIt(): ${builderRequest.returnTypeFqn} =
            owner.${builderRequest.methodName}(${argsInterfaceInfo.name}Impl(
            ${argsInterfaceInfo.propertyNames.joinToString("\n") { name -> "$name = $name," }}
            ))
        }
        """
      }

      text += "}"

      filesToWrite[Path.of(genSrcDirName.toString(), sourcePackage.replace('.', '/'), "${cls.name}Helpers.kt")] = fileHeader + text

      for ((argsInterfaceFqn, argsInterfaceInfo) in argsInterfacesByFqn) {
        text = """
        @GeneratedBuilder.Result
        class ${argsInterfaceInfo.name}Builder(
        ${
          argsInterfaceInfo.requiredArguments.joinToString("\n") { prop ->
            "${prop.kdoc.renderKdoc()}private var ${prop.name}: ${prop.typeFqn},"
          }
        }
        ) {
        ${
          argsInterfaceInfo.optionalArguments.joinToString("\n\n") { prop ->
            "private var ${prop.name}: ${prop.typeFqn} = ${prop.body}"
          }
        }
        ${
          argsInterfaceInfo.propertyNames.joinToString("\n") { name ->
            renderPropertyInBuilder(
              tempProject,
              name,
              "${argsInterfaceInfo.name}Builder",
              argsInterfaceInfo.requiredArguments,
              argsInterfaceInfo.optionalArguments,
            )
          }
        }
    
          fun build(): ${argsInterfaceInfo.name} =
            ${argsInterfaceInfo.name}Impl(
            ${argsInterfaceInfo.propertyNames.joinToString("\n") { name -> "$name = $name," }}
            )
        }
    
        @GeneratedBuilder.Result
        internal class ${argsInterfaceInfo.name}Impl(
        ${
          argsInterfaceInfo.propertyNames.joinToString("\n") { name ->
            val typeFqn =
              argsInterfaceInfo.requiredArguments.firstOrNull { it.name == name }?.typeFqn
              ?: argsInterfaceInfo.optionalArguments.first { it.name == name }.typeFqn
            "override val $name: $typeFqn,"
          }
        }
        ) : $argsInterfaceFqn"""

        filesToWrite[Path.of(genSrcDirName.toString(), sourcePackage.replace('.', '/'), "${argsInterfaceInfo.name}Builder.kt")] =
          fileHeader + text
      }
    }

    for ((filePath, text) in filesToWrite) {
      filesContent.compute(filePath) { _, old ->
        (old?.first ?: Optional.empty()) to Optional.of(text)
      }
    }
  }

  return filesContent
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
private class ArgInterfaceInfo(
  val name: String,
  val requiredArguments: List<RequiredArgument>,
  val optionalArguments: List<OptionalArgument>,
  val propertyNames: List<String>,
)