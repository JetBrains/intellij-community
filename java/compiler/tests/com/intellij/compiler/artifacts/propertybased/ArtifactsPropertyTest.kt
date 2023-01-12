// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.artifacts.propertybased

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.packaging.artifacts.*
import com.intellij.packaging.elements.*
import com.intellij.packaging.impl.artifacts.PlainArtifactType
import com.intellij.packaging.impl.artifacts.workspacemodel.ArtifactBridge
import com.intellij.packaging.impl.artifacts.workspacemodel.ArtifactManagerBridge.Companion.artifactsMap
import com.intellij.packaging.impl.artifacts.workspacemodel.forThisAndFullTree
import com.intellij.packaging.impl.elements.ArtifactRootElementImpl
import com.intellij.packaging.impl.elements.DirectoryPackagingElement
import com.intellij.packaging.impl.elements.FileCopyPackagingElement
import com.intellij.packaging.ui.ArtifactEditorContext
import com.intellij.packaging.ui.PackagingElementPresentation
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.UsefulTestCase.assertNotEmpty
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.testFramework.workspaceModel.updateProjectModel
import com.intellij.util.ui.EmptyIcon
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.addArtifactEntity
import com.intellij.workspaceModel.storage.bridgeEntities.addArtifactRootElementEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ArtifactEntity
import com.intellij.workspaceModel.storage.bridgeEntities.CompositePackagingElementEntity
import com.intellij.workspaceModel.storage.bridgeEntities.PackagingElementEntity
import com.intellij.workspaceModel.storage.bridgeEntities.modifyEntity
import com.intellij.workspaceModel.storage.impl.VersionedEntityStorageImpl
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.ImperativeCommand
import org.jetbrains.jetCheck.PropertyChecker
import org.junit.Assert.*
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.util.function.Supplier
import javax.swing.Icon

class ArtifactsPropertyTest {
  companion object {
    @ClassRule
    @JvmField
    val application = ApplicationRule()

    private const val MAX_ARTIFACT_NUMBER = 50
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  @Rule
  @JvmField
  val disposableRule = DisposableRule()

  // This is a code generator for failed tests.
  // At the moment it's incomplete and should be updated if some execution paths are missing
  lateinit var codeMaker: CodeMaker

  @Test
  fun `property test`() {
    val writeDisposable = writeActionDisposable(disposableRule.disposable)
    invokeAndWaitIfNeeded {
      PackagingElementType.EP_NAME.point.registerExtension(MyWorkspacePackagingElementType, writeDisposable)
      PackagingElementType.EP_NAME.point.registerExtension(MyCompositeWorkspacePackagingElementType, writeDisposable)
      customArtifactTypes.forEach {
        ArtifactType.EP_NAME.point.registerExtension(it, writeDisposable)
      }
    }

    PropertyChecker.checkScenarios {
      codeMaker = CodeMaker()
      ImperativeCommand {
        try {
          it.executeCommands(Generator.sampledFrom(
            RenameArtifact(),
            AddArtifact(),
            RemoveArtifact(),
            ChangeBuildOnMake(),
            ChangeArtifactType(),

            AddPackagingElementTree(),
            GetPackagingElement(),
            FindCompositeChild(),
            RemoveAllChildren(),

            GetAllArtifacts(),
            GetSortedArtifacts(),
            GetAllArtifactsIncludingInvalid(),

            FindByNameExisting(),
            FindByNameNonExisting(),
            FindByType(),

            CreateViaWorkspaceModel(),
            RenameViaWorkspaceModel(),
            ChangeOnBuildViaWorkspaceModel(),
            ChangeArtifactTypeViaWorkspaceModel(),
            RemoveViaWorkspaceModel(),
          ))
        }
        finally {
          codeMaker.finish()
          makeChecksHappy {
            val artifacts = ArtifactManager.getInstance(projectModel.project).artifacts
            val modifiableModel = ArtifactManager.getInstance(projectModel.project).createModifiableModel()
            artifacts.forEach {
              modifiableModel.removeArtifact(it)
            }
            modifiableModel.commit()

            WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
              it.replaceBySource({ true }, MutableEntityStorage.create())
            }
          }

          it.logMessage("------- Code -------")
          it.logMessage(codeMaker.get())
        }
      }
    }
  }

  inner class GetPackagingElement : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val artifactBridge = selectArtifactBridge(env, "get packaging element") ?: return
      makeChecksHappy {
        val modifiableModel = ArtifactManager.getInstance(projectModel.project).createModifiableModel()
        val modifiableArtifact = modifiableModel.getOrCreateModifiableArtifact(artifactBridge)
        val (parent, child) = chooseSomeElementFromTree(env, modifiableArtifact)
        if (parent == null) {
          modifiableModel.dispose()
          return@makeChecksHappy
        }

        val newChild = parent.addOrFindChild(child)
        checkResult(env) {
          assertSame(child, newChild)
        }
        modifiableModel.commit()
      }
    }
  }

  inner class FindCompositeChild : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val artifactBridge = selectArtifactBridge(env, "get packaging element") ?: return
      makeChecksHappy {
        val modifiableModel = ArtifactManager.getInstance(projectModel.project).createModifiableModel()
        val modifiableArtifact = modifiableModel.getOrCreateModifiableArtifact(artifactBridge)
        val (parent, child) = chooseSomeElementFromTree(env, modifiableArtifact)
        if (parent == null || child !is CompositePackagingElement<*>) {
          modifiableModel.dispose()
          return@makeChecksHappy
        }

        val names = parent.children.filterIsInstance<CompositePackagingElement<*>>().map { it.name }
        if (names.size != names.toSet().size) {
          modifiableModel.dispose()
          return@makeChecksHappy
        }
        val newChild = parent.findCompositeChild(child.name)
        checkResult(env) {
          assertSame(child, newChild)
        }
        modifiableModel.commit()
      }
    }
  }

  inner class RemoveAllChildren : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val artifactBridge = selectArtifactBridge(env, "get packaging element") ?: return
      val (rootElement, removedChild, parent) = makeChecksHappy {
        val modifiableModel = ArtifactManager.getInstance(projectModel.project).createModifiableModel()
        val modifiableArtifact = modifiableModel.getOrCreateModifiableArtifact(artifactBridge)

        val modifiableModelVal = codeMaker.makeVal("modifiableModel", "ArtifactManager.getInstance(project).createModifiableModel()")
        val modifiableArtifactVal = codeMaker.makeVal("modifiableArtifact", "${modifiableModelVal}.getOrCreateModifiableArtifact(${
          codeMaker.v("chosenArtifact")
        })")

        val (parent, child) = chooseSomeElementFromTree(env, modifiableArtifact)
        if (parent == null) {
          modifiableModel.dispose()
          return@makeChecksHappy null
        }

        parent.removeAllChildren()

        env.logMessage("Removing some package element for ${artifactBridge.name}")
        codeMaker.addLine("${codeMaker.v("chosenParent")}.removeAllChildren()")

        // It's important to get root element
        //   Otherwise diff will be injected into the root element
        val rootElement = modifiableArtifact.rootElement
        modifiableModel.commit()
        val rootElementVal = codeMaker.makeVal("rootElement", "$modifiableArtifactVal.rootElement")
        codeMaker.addLine("$modifiableModelVal.commit()")
        codeMaker.addLine("return@runWriteAction Triple($rootElementVal, ${codeMaker.v("chosenChild")}, ${codeMaker.v("chosenParent")})")
        Triple(rootElement, child, parent)
      } ?: return

      checkResult(env) {
        val manager = ArtifactManager.getInstance(projectModel.project)
        val foundArtifact = runReadAction { manager.findArtifact(artifactBridge.name) }!!
        val managerVal = codeMaker.makeVal("manager", "ArtifactManager.getInstance(project)")
        val foundArtifactVal = codeMaker.makeVal("foundArtifact",
                                                 "$managerVal.findArtifact(${codeMaker.v("chosenArtifact")}.name)!!")

        val artifactEntity = WorkspaceModel.getInstance(projectModel.project).entityStorage.current
          .entities(ArtifactEntity::class.java).find { it.name == artifactBridge.name }!!

        assertElementsEquals(rootElement, foundArtifact.rootElement)

        assertTreesEquals(projectModel.project, foundArtifact.rootElement, artifactEntity.rootElement!!)

        codeMaker.scope("$foundArtifactVal.rootElement.forThisAndFullTree") {
          codeMaker.scope("if (it === ${codeMaker.v("happyResult")}.third)") {
            codeMaker.addLine("assertTrue(it.children.none { it.isEqualTo(${codeMaker.v("happyResult")}.second) })")
          }
        }
        foundArtifact.rootElement.forThisAndFullTree {
          if (it === parent) {
            assertTrue(it.children.none { it.isEqualTo(removedChild) })
          }
        }
      }
    }
  }

  inner class AddPackagingElementTree : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val (selectedArtifact, manager) = selectArtifactViaBridge(env, "adding package element") ?: return

      env.logMessage("Add new packaging elements tree to: ${selectedArtifact.name}")

      val rootElement = makeChecksHappy {
        val modifiableModel = manager.createModifiableModel()
        val modifiableArtifact = modifiableModel.getOrCreateModifiableArtifact(selectedArtifact)

        val (parent, _) = chooseSomeElementFromTree(env, modifiableArtifact)
        val newTree = makeElementsTree(env).first

        parent?.let { addChildSomehow(env, it, newTree) }

        // It's important to get root element
        //   Otherwise diff will be injected into the root element
        val rootElement = modifiableArtifact.rootElement
        modifiableModel.commit()

        rootElement
      }

      checkResult(env) {
        val foundArtifact = runReadAction { manager.findArtifact(selectedArtifact.name) }!!

        val artifactEntity = WorkspaceModel.getInstance(projectModel.project).entityStorage.current
          .entities(ArtifactEntity::class.java).find { it.name == selectedArtifact.name }!!

        assertElementsEquals(rootElement, foundArtifact.rootElement)

        assertTreesEquals(projectModel.project, foundArtifact.rootElement, artifactEntity.rootElement!!)
      }
    }

    private fun addChildSomehow(env: ImperativeCommand.Environment,
                                parent: CompositePackagingElement<*>,
                                newTree: PackagingElement<*>) {
      when (env.generateValue(Generator.integers(0, 1), null)) {
        0 -> parent.addOrFindChild(newTree)
        1 -> parent.addFirstChild(newTree)
        else -> error("Unexpected")
      }
    }
  }

  inner class CreateViaWorkspaceModel : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val workspaceModel = WorkspaceModel.getInstance(projectModel.project)
      val artifactName = selectArtifactName(
        env,
        workspaceModel.entityStorage
          .current
          .entities(ArtifactEntity::class.java)
          .map { it.name }
          .toList()
      ) ?: run {
        env.logMessage("Cannot select name for new artifact via workspace model")
        return
      }

      makeChecksHappy {
        workspaceModel.updateProjectModel {
          val rootElement = createCompositeElementEntity(env, it)
          val (_, id, _) = selectArtifactType(env)
          it.addArtifactEntity(artifactName, id, true, null, rootElement, TestEntitySource)
        }
      }
      env.logMessage("Add artifact via model: $artifactName")

      checkResult(env) {
        val foundArtifact = runReadAction { ArtifactManager.getInstance(projectModel.project).findArtifact(artifactName) }
        assertNotNull(foundArtifact)
      }
    }
  }

  inner class RenameViaWorkspaceModel : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val workspaceModel = WorkspaceModel.getInstance(projectModel.project)
      val artifactName = selectArtifactName(
        env,
        workspaceModel.entityStorage
          .current
          .entities(ArtifactEntity::class.java)
          .map { it.name }
          .toList()
      ) ?: run {
        env.logMessage("Cannot select name for new artifact via workspace model")
        return
      }

      val selectedArtifact = selectArtifactViaModel(env, workspaceModel, "renaming") ?: return
      env.logMessage("Rename artifact via workspace model: ${selectedArtifact.name} -> $artifactName")
      makeChecksHappy {
        workspaceModel.updateProjectModel {
          it.modifyEntity(selectedArtifact) {
            this.name = artifactName
          }
        }
      }

      checkResult(env) {
        val entities = workspaceModel.entityStorage.current.entities(ArtifactEntity::class.java)
        assertTrue(entities.none { it.name == selectedArtifact.name })
        assertTrue(entities.any { it.name == artifactName })

        onManager(env) { manager ->
          val allArtifacts = runReadAction{ manager.artifacts }
          assertTrue(allArtifacts.none { it.name == selectedArtifact.name })
          assertTrue(allArtifacts.any { it.name == artifactName })
        }
      }
    }
  }

  inner class ChangeOnBuildViaWorkspaceModel : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val workspaceModel = WorkspaceModel.getInstance(projectModel.project)

      val selectedArtifact = selectArtifactViaModel(env, workspaceModel, "changing option") ?: return
      env.logMessage("Change build on make option for ${selectedArtifact.name}: Prev value: ${selectedArtifact.includeInProjectBuild}")
      makeChecksHappy {
        workspaceModel.updateProjectModel {
          it.modifyEntity(selectedArtifact) {
            this.includeInProjectBuild = !this.includeInProjectBuild
          }
        }
      }

      checkResult(env) {
        val artifactEntity = workspaceModel.entityStorage.current.resolve(selectedArtifact.symbolicId)!!
        assertEquals(!selectedArtifact.includeInProjectBuild, artifactEntity.includeInProjectBuild)

        onManager(env) { manager ->
          val artifact = runReadAction { manager.findArtifact(selectedArtifact.name) }!!
          assertEquals(!selectedArtifact.includeInProjectBuild, artifact.isBuildOnMake)
        }
      }
    }
  }

  inner class ChangeArtifactTypeViaWorkspaceModel : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val workspaceModel = WorkspaceModel.getInstance(projectModel.project)

      val selectedArtifact = selectArtifactViaModel(env, workspaceModel, "changing artifact type") ?: return
      val (_, id, _) = selectArtifactType(env)
      env.logMessage("Change artifact type for ${selectedArtifact.name}: Prev value: ${selectedArtifact.artifactType}")
      makeChecksHappy {
        workspaceModel.updateProjectModel {
          it.modifyEntity(selectedArtifact) {
            this.artifactType = id
          }
        }
      }

      checkResult(env) {
        val artifactEntity = artifactEntity(projectModel.project, selectedArtifact.name)
        assertEquals(id, artifactEntity.artifactType)

        onManager(env) { manager ->
          val artifact = runReadAction { manager.findArtifact(selectedArtifact.name)!! }
          assertEquals(id, artifact.artifactType.id)
        }
      }
    }
  }

  inner class RemoveViaWorkspaceModel : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val workspaceModel = WorkspaceModel.getInstance(projectModel.project)

      val selectedArtifact = selectArtifactViaModel(env, workspaceModel, "removing") ?: return
      env.logMessage("Remove artifact: ${selectedArtifact.name}")
      makeChecksHappy {
        workspaceModel.updateProjectModel {
          it.removeEntity(selectedArtifact)
        }
      }

      checkResult(env) {
        val entities = workspaceModel.entityStorage.current.entities(ArtifactEntity::class.java)
        assertTrue(entities.none { it.name == selectedArtifact.name })

        onManager(env) { manager ->
          val allArtifacts = runReadAction { manager.artifacts }
          assertTrue(allArtifacts.none { it.name == selectedArtifact.name })
        }
      }
    }
  }

  inner class FindByType : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val (selectedArtifact, _) = selectArtifactViaBridge(env, "finding by type") ?: return
      val searchType = selectedArtifact.artifactType.id
      env.logMessage("Search for artifact by type: $searchType")

      onManager(env) { manager ->
        assertNotEmpty(runReadAction { manager.getArtifactsByType(ArtifactType.findById(searchType)!!) })
      }
    }
  }

  inner class FindByNameNonExisting : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val artifactEntities = WorkspaceModel.getInstance(projectModel.project).entityStorage.current.entities(
        ArtifactEntity::class.java).toList()

      val artifactName = selectArtifactName(env, artifactEntities.map { it.name }) ?: run {
        env.logMessage("Cannot select non-existing name for search")
        return
      }
      env.logMessage("Search for artifact by name: $artifactName")

      onManager(env) { manager ->
        assertNull(runReadAction { manager.findArtifact (artifactName) })
      }
    }
  }

  inner class FindByNameExisting : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val artifactEntities = WorkspaceModel.getInstance(projectModel.project).entityStorage.current.entities(
        ArtifactEntity::class.java).toList()
      if (artifactEntities.isEmpty()) {
        env.logMessage("Cannot select artifact for finding")
        return
      }

      val artifactName = env.generateValue(Generator.sampledFrom(artifactEntities), null).name
      env.logMessage("Search for artifact by name: $artifactName")

      onManager(env) { manager ->
        assertNotNull(runReadAction { manager.findArtifact (artifactName) })
      }
    }
  }

  inner class GetAllArtifactsIncludingInvalid : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val manager = ArtifactManager.getInstance(projectModel.project)
      env.logMessage("Get all artifacts including invalid")
      val artifacts = runReadAction {  manager.allArtifactsIncludingInvalid }
      artifacts.forEach { _ ->
        // Nothing
      }
    }
  }

  inner class GetAllArtifacts : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val manager = ArtifactManager.getInstance(projectModel.project)
      env.logMessage("Get all artifacts")
      val artifacts = runReadAction { manager.artifacts }
      artifacts.forEach { _ ->
        // Nothing
      }
    }
  }

  inner class GetSortedArtifacts : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val manager = ArtifactManager.getInstance(projectModel.project)
      env.logMessage("Get all artifacts sorted")
      val artifacts = runReadAction { manager.sortedArtifacts }
      artifacts.forEach { _ ->
        // Nothing
      }
    }
  }

  inner class RemoveArtifact : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val (artifactForRemoval, _) = selectArtifactViaBridge(env, "removing") ?: return

      val manager = ArtifactManager.getInstance(projectModel.project)
      val initialArtifactsSize = runReadAction { manager.artifacts }.size

      val removalName = artifactForRemoval.name
      invokeAndWaitIfNeeded {
        runWriteAction {
          val modifiableModel = manager.createModifiableModel()
          modifiableModel.removeArtifact(artifactForRemoval)
          modifiableModel.commit()
        }
      }

      checkResult(env) {
        val newArtifactsList = runReadAction { manager.artifacts }
        assertEquals(initialArtifactsSize - 1, newArtifactsList.size)
        assertTrue(newArtifactsList.none { it.name == removalName })

        val artifactEntities = WorkspaceModel.getInstance(projectModel.project).entityStorage.current.entities(ArtifactEntity::class.java)
        assertTrue(artifactEntities.none { it.name == removalName })
      }
    }
  }

  inner class AddArtifact : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val artifactName = selectArtifactName(env)
      val createRootElement = env.generateValue(Generator.sampledFrom(false, true, true, true, true, true), null)
      val (rootElement, rootVal) = if (createRootElement) createCompositeElement(env) else null to null
      val newArtifact = makeChecksHappy {
        val (instance, _, typeVal) = selectArtifactType(env)
        codeMaker.addLine("ArtifactManager.getInstance(project).addArtifact(\"$artifactName\", $typeVal, $rootVal)")
        ArtifactManager.getInstance(projectModel.project).addArtifact(artifactName, instance, rootElement)
      }
      val newArtifactName = newArtifact.name
      env.logMessage("Add new artifact via bridge: $newArtifactName. Final name: $newArtifactName")

      checkResult(env) {
        val bridgeVal = codeMaker.makeVal("bridgeArtifact", "artifact(project, \"$newArtifactName\")")
        val bridgeArtifact = artifact(projectModel.project, newArtifactName)

        val artifactEntityVal = codeMaker.makeVal("artifactEntity", "artifactEntity(project, \"$newArtifactName\")")
        val artifactEntity = artifactEntity(projectModel.project, newArtifactName)

        codeMaker.addLine("assertTreesEquals(project, $bridgeVal.rootElement, $artifactEntityVal.rootElement)")
        assertTreesEquals(projectModel.project, bridgeArtifact.rootElement, artifactEntity.rootElement!!)
      }
    }
  }

  inner class RenameArtifact : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val manager = ArtifactManager.getInstance(projectModel.project)
      val artifacts = runReadAction { manager.artifacts }
      if (artifacts.isEmpty()) return

      val index = env.generateValue(Generator.integers(0, artifacts.lastIndex), null)
      val newName = selectArtifactName(env, artifacts.map { it.name }) ?: run {
        env.logMessage("Cannot select name for new artifact via workspace model")
        return
      }

      val artifact = artifacts[index]
      val oldName = artifact.name
      env.logMessage("Rename artifact: $oldName -> $newName")
      makeChecksHappy {
        val modifiableModel = manager.createModifiableModel()
        val modifiableArtifact = modifiableModel.getOrCreateModifiableArtifact(artifact)
        modifiableArtifact.name = newName
        modifiableModel.commit()
      }

      checkResult(env) {
        assertEquals(newName, runReadAction{ manager.artifacts }[index].name)

        val artifactEntities = WorkspaceModel.getInstance(projectModel.project).entityStorage.current.entities(ArtifactEntity::class.java)
        assertTrue(artifactEntities.any { it.name == newName })
        assertTrue(artifactEntities.none { it.name == oldName })
      }
    }
  }

  inner class ChangeBuildOnMake : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val manager = ArtifactManager.getInstance(projectModel.project)
      val artifacts = runReadAction {  manager.artifacts }
      if (artifacts.isEmpty()) return

      val index = env.generateValue(Generator.integers(0, artifacts.lastIndex), null)
      val artifact = artifacts[index]
      val oldBuildOnMake = artifact.isBuildOnMake
      env.logMessage("Change isBuildOnMake for ${artifact.name}. New value: ${oldBuildOnMake}")
      makeChecksHappy {
        val modifiableModel = manager.createModifiableModel()
        val modifiableArtifact = modifiableModel.getOrCreateModifiableArtifact(artifact)
        modifiableArtifact.isBuildOnMake = !modifiableArtifact.isBuildOnMake
        modifiableModel.commit()
      }

      checkResult(env) {
        assertEquals(!oldBuildOnMake, runReadAction{ manager.artifacts }[index].isBuildOnMake)

        val artifactEntities = WorkspaceModel.getInstance(projectModel.project).entityStorage.current.entities(ArtifactEntity::class.java)
        assertTrue(artifactEntities.single { it.name == artifact.name }.includeInProjectBuild == !oldBuildOnMake)
      }
    }
  }

  inner class ChangeArtifactType : ImperativeCommand {
    override fun performCommand(env: ImperativeCommand.Environment) {
      val artifact = selectArtifactBridge(env, "change artifact type") ?: return
      val artifactVal = codeMaker.v("chosenArtifact")
      val (newArtifactType, id, typeVal) = selectArtifactType(env)
      env.logMessage("Change artifact type for ${artifact.name}. New value: ${newArtifactType}")
      makeChecksHappy {
        modifyArtifact(artifact, artifactVal) {
          codeMaker.addLine("$it.artifactType = $typeVal")
          artifactType = newArtifactType
        }
      }

      checkResult(env) {
        assertEquals(newArtifactType, artifact(projectModel.project, artifact.name).artifactType)

        val artifactEntityVal = codeMaker.makeVal("artifactEntity", "artifactEntity(project, \"${artifact.name}\")")
        codeMaker.addLine("assertTrue($artifactEntityVal.artifactType == \"$id\")")
        val artifactEntity = artifactEntity(projectModel.project, artifact.name)
        assertTrue(artifactEntity.artifactType == id)
      }
    }
  }

  private fun modifyArtifact(artifact: Artifact, artifactVal: String, modification: ModifiableArtifact.(String) -> Unit) {
    val modifiableModel = ArtifactManager.getInstance(projectModel.project).createModifiableModel()
    val modifiableArtifact = modifiableModel.getOrCreateModifiableArtifact(artifact)

    val modifiableModelVal = codeMaker.makeVal("modifiableModel", "ArtifactManager.getInstance(project).createModifiableModel()")
    val modifiableArtifactVal = codeMaker.makeVal("modifiableArtifact", "$modifiableModelVal.getOrCreateModifiableArtifact($artifactVal)")

    modifiableArtifact.modification(modifiableArtifactVal)

    codeMaker.addLine("$modifiableModelVal.commit()")

    modifiableModel.commit()
  }

  private fun selectArtifactName(env: ImperativeCommand.Environment): String {
    return "Artifact-${env.generateValue(Generator.integers(0, MAX_ARTIFACT_NUMBER), null)}"
  }

  private fun selectArtifactName(env: ImperativeCommand.Environment, notLike: List<String>): String? {
    var counter = 50
    while (counter > 0) {
      val name = selectArtifactName(env)
      if (name !in notLike) return name
      counter--
    }
    return null
  }

  private fun createCompositeElement(env: ImperativeCommand.Environment): Pair<CompositePackagingElement<*>, String> {
    val root = ArtifactRootElementImpl()
    val rootVal = codeMaker.makeVal("artifactRoot", "ArtifactRootElementImpl()")
    val value = env.generateValue(Generator.booleans(), null)
    if (value) {
      root.addFirstChild(makeElementsTree(env).first)
      codeMaker.addLine("$rootVal.addFirstChild(${codeMaker.v("element_0")})")
    }
    return root to rootVal
  }

  private fun makeElementsTree(env: ImperativeCommand.Environment, depth: Int = 0): Pair<PackagingElement<*>, String> {
    val value = env.generateValue(Generator.integers(0, 3), null)
    val indent = "  ".repeat(depth)
    val (element, elementName) = when (value) {
      0 -> {
        val directoryName = env.generateValue(Generator.sampledFrom(names), null)
        val element = DirectoryPackagingElement(directoryName)
        val currentElementName = codeMaker.makeVal("element_$depth", "DirectoryPackagingElement(\"$directoryName\")")
        env.logMessage("${indent}Generate DirectoryPackagingElement: $directoryName")
        element to currentElementName
      }
      1 -> {
        val outputName = env.generateValue(Generator.sampledFrom(names), null)
        val pathName = "/" + env.generateValue(Generator.sampledFrom(names), null)
        env.logMessage("${indent}Generate FileCopyPackagingElement ($pathName -> $outputName)")
        val currentElementName = codeMaker.makeVal("element_$depth", "FileCopyPackagingElement(\"$pathName\", \"$outputName\")")
        FileCopyPackagingElement(pathName, outputName) to currentElementName
      }
      2 -> {
        val data = env.generateValue(Generator.sampledFrom(names), null)
        env.logMessage("${indent}Generate MyWorkspacePackagingElement ($data)")
        val currentElementName = codeMaker.makeVal("element_$depth", "MyWorkspacePackagingElement(\"$data\")")
        MyWorkspacePackagingElement(data) to currentElementName
      }
      3 -> {
        val data = env.generateValue(Generator.sampledFrom(names), null)
        val name = env.generateValue(Generator.sampledFrom(names), null)
        env.logMessage("${indent}Generate MyCompositeWorkspacePackagingElement ($data, $name)")
        val currentElementName = codeMaker.makeVal("element_$depth", "MyCompositeWorkspacePackagingElement(\"$data\", \"$name\")")
        MyCompositeWorkspacePackagingElement(data, name) to currentElementName
      }
      else -> error("Unexpected branch")
    }

    if (element is CompositePackagingElement<*>) {
      if (depth < 5) {
        // This is all magic numbers. Just trying to make less children for deeper layers of package elements tree
        val maxChildren = when {
          depth == 0 -> 5
          depth in 1..2 -> 3
          depth in 3..4 -> 2
          depth > 4 -> 1
          else -> 0
        }
        val amountOfChildren = env.generateValue(Generator.integers(0, maxChildren), null)
        env.logMessage("${indent}- Generate $amountOfChildren children:")
        for (i in 0 until amountOfChildren) {
          val (child, name) = makeElementsTree(env, depth + 1)
          element.addFirstChild(child)
          codeMaker.addLine("$elementName.addFirstChild($name)")
        }
      }
    }

    return element to elementName
  }

  private fun createCompositeElementEntity(env: ImperativeCommand.Environment,
                                           builder: MutableEntityStorage): CompositePackagingElementEntity {
    return builder.addArtifactRootElementEntity(emptyList(), TestEntitySource)
  }

  private fun chooseSomeElementFromTree(env: ImperativeCommand.Environment,
                                        artifact: Artifact): Pair<CompositePackagingElement<*>?, PackagingElement<*>> {
    val root = artifact.rootElement
    val allElements: MutableList<Pair<CompositePackagingElement<*>?, PackagingElement<*>>> = mutableListOf(null to root)
    flatElements(root, allElements)
    val (parent, child) = env.generateValue(Generator.sampledFrom(allElements), null)

    val artifactVal = codeMaker.vOrNull("modifiableArtifact") ?: codeMaker.v("chosenArtifact")
    var rootElementVal = codeMaker.makeVal("rootElement", "$artifactVal.rootElement")
    if (root != child) {
      val address = generateAddress(root, child)!!
      var childElementVal = "($rootElementVal as CompositePackagingElement<*>).children[${address[0]}]"
      address.drop(1).forEach {
        rootElementVal = childElementVal
        childElementVal = "($rootElementVal as CompositePackagingElement<*>).children[$it]"
      }
      codeMaker.makeVal("chosenParent", "$rootElementVal  as CompositePackagingElement<*>")
      codeMaker.makeVal("chosenChild", childElementVal)
    }
    else {
      codeMaker.makeVal("chosenParent", null)
      codeMaker.makeVal("chosenChild", rootElementVal)
    }

    return parent to child
  }

  private fun generateAddress(root: CompositePackagingElement<*>, child: PackagingElement<*>): List<Int>? {
    root.children.forEachIndexed { index, packagingElement ->
      val indexList = listOf(index)
      if (packagingElement === child) return indexList
      if (packagingElement is CompositePackagingElement<*>) {
        val result = generateAddress(packagingElement, child)
        if (result != null) {
          return indexList + result
        }
      }
    }
    return null
  }

  private fun generateAddress(root: CompositePackagingElementEntity, child: PackagingElementEntity): List<Int>? {
    root.children.forEachIndexed { index, packagingElement ->
      if (packagingElement == child) return listOf(index)
      if (packagingElement is CompositePackagingElementEntity) {
        val result = generateAddress(packagingElement, child)
        if (result != null) {
          return result
        }
      }
    }
    return null
  }

  private fun selectArtifactType(env: ImperativeCommand.Environment): Triple<ArtifactType, String, String> {
    val selector = env.generateValue(Generator.integers(0, allArtifactTypes.lastIndex), null)
    val artifactVal = codeMaker.makeVal("artifactType", "allArtifactTypes[$selector]")
    val instance = allArtifactTypes[selector]
    return Triple(instance, instance.id, artifactVal)
  }

  private fun flatElements(currentElement: CompositePackagingElement<*>,
                           result: MutableList<Pair<CompositePackagingElement<*>?, PackagingElement<*>>>) {
    currentElement.children.forEach {
      result.add(currentElement to it)
      if (it is CompositePackagingElement<*>) {
        flatElements(it, result)
      }
    }
  }

  private fun selectArtifactViaBridge(env: ImperativeCommand.Environment, reason: String): Pair<Artifact, ArtifactManager>? {
    val manager = ArtifactManager.getInstance(projectModel.project)
    val artifacts = runReadAction { manager.artifacts }
    if (artifacts.isEmpty()) {
      env.logMessage("Cannot select artifact for $reason")
      return null
    }

    val selectedArtifact = env.generateValue(Generator.sampledFrom(*artifacts), null)
    val artifactIndex = artifacts.indexOf(selectedArtifact)
    val managerVal = codeMaker.makeVal("manager", "ArtifactManager.getInstance(project)")
    val artifactsVal = codeMaker.makeVal("artifacts", "$managerVal.artifacts")
    codeMaker.makeVal("chosenArtifact", "$artifactsVal[$artifactIndex]")

    return selectedArtifact to manager
  }

  fun selectArtifactViaModel(env: ImperativeCommand.Environment, workspaceModel: WorkspaceModel, reason: String): ArtifactEntity? {
    val existingArtifacts = workspaceModel.entityStorage.current.entities(ArtifactEntity::class.java).toList()
    if (existingArtifacts.isEmpty()) {
      env.logMessage("Cannot select artifact for $reason")
      return null
    }
    val selectedArtifact = env.generateValue(Generator.sampledFrom(existingArtifacts), null)

    val artifactEntityId = existingArtifacts.indexOf(selectedArtifact)

    codeMaker.makeVal("chosenArtifactEntity", "WorkspaceModel.getInstance(project).entityStorage.current.entities(ArtifactEntity::class.java).toList()[$artifactEntityId]")

    return selectedArtifact
  }

  private fun selectArtifactBridge(env: ImperativeCommand.Environment, reason: String): Artifact? {
    val viaBridge = env.generateValue(Generator.booleans(), null)
    return if (viaBridge) {
      selectArtifactViaBridge(env, reason)?.first
    }
    else {
      val workspaceModel = WorkspaceModel.getInstance(projectModel.project)
      val entity = selectArtifactViaModel(env, workspaceModel, reason) ?: return null

      codeMaker.makeVal("chosenArtifact", "WorkspaceModel.getInstance(project).entityStorage.current.artifactsMap.getDataByEntity(${codeMaker.v("chosenArtifactEntity")})")

      workspaceModel.entityStorage.current.artifactsMap.getDataByEntity(entity)
    }
  }

  private inline fun checkResult(env: ImperativeCommand.Environment, action: () -> Unit) {
    val checkResult = env.generateValue(Generator.booleans(), null)
    env.logMessage("Check result: $checkResult")
    if (checkResult) {
      action()
    }

    assertArtifactsHaveStableStore()
  }

  private inline fun onManager(env: ImperativeCommand.Environment, action: (ArtifactModel) -> Unit) {
    val onModifiableModel = env.generateValue(Generator.booleans(), null)

    val manager = if (onModifiableModel) {
      ArtifactManager.getInstance(projectModel.project).createModifiableModel()
    }
    else {
      ArtifactManager.getInstance(projectModel.project)
    }

    action(manager)

    if (onModifiableModel) (manager as ModifiableArtifactModel).dispose()
  }

  private fun assertArtifactsHaveStableStore() {
    val manager = ArtifactManager.getInstance(projectModel.project)
    runReadAction{ manager.artifacts }.forEach {
      assertTrue((it as ArtifactBridge).entityStorage is VersionedEntityStorageImpl)
    }
  }

  private val names = List(20) { "Name-$it" }

  private fun <T> makeChecksHappy(action: () -> T): T {
    return invokeAndWaitIfNeeded {
      runWriteAction {
        codeMaker.startScope("happyResult", "invokeAndWaitIfNeeded")
        codeMaker.startScope("runWriteAction")
        try {
          return@runWriteAction action()
        }
        finally {
          if (codeMaker.last()?.trimIndent()?.startsWith("return") != true) {
            codeMaker.addLine("return@runWriteAction null")
          }
          codeMaker.finishScope()
          codeMaker.finishScope()
        }
      }
    }
  }

  private fun writeActionDisposable(parent: Disposable): Disposable {
    val writeDisposable = Disposer.newDisposable()
    Disposer.register(parent) {
      invokeAndWaitIfNeeded {
        runWriteAction {
          Disposer.dispose(writeDisposable)
        }
      }
    }
    return writeDisposable
  }
}

object TestEntitySource : EntitySource

class MyWorkspacePackagingElement(data: String) : PackagingElement<MyWorkspacePackagingElementState>(PackagingElementType.EP_NAME.findExtensionOrFail(MyWorkspacePackagingElementType::class.java)) {

  constructor(): this("")

  private val state: MyWorkspacePackagingElementState = MyWorkspacePackagingElementState(data)

  override fun getState(): MyWorkspacePackagingElementState = state

  override fun loadState(state: MyWorkspacePackagingElementState) {
    this.state.data = state.data
  }

  override fun isEqualTo(element: PackagingElement<*>): Boolean = (element as? MyWorkspacePackagingElement)?.state?.data == state.data

  override fun createPresentation(context: ArtifactEditorContext): PackagingElementPresentation {
    throw UnsupportedOperationException()
  }
}

class MyWorkspacePackagingElementState(var data: String = "")

object MyWorkspacePackagingElementType : PackagingElementType<MyWorkspacePackagingElement>("Custom-element", Supplier { "Custom Element" }) {
  override fun canCreate(context: ArtifactEditorContext, artifact: Artifact): Boolean = true

  override fun chooseAndCreate(context: ArtifactEditorContext,
                               artifact: Artifact,
                               parent: CompositePackagingElement<*>): MutableList<out PackagingElement<*>> {
    throw UnsupportedOperationException()
  }

  override fun createEmpty(project: Project): MyWorkspacePackagingElement {
    return MyWorkspacePackagingElement()
  }
}

class MyCompositeWorkspacePackagingElement(data: String, name: String) : CompositePackagingElement<MyCompositeWorkspacePackagingElementState>(PackagingElementType.EP_NAME.findExtensionOrFail(MyCompositeWorkspacePackagingElementType::class.java)) {

  constructor(): this("", "")

  private val state: MyCompositeWorkspacePackagingElementState = MyCompositeWorkspacePackagingElementState(data, name)

  override fun getState(): MyCompositeWorkspacePackagingElementState = state

  override fun loadState(state: MyCompositeWorkspacePackagingElementState) {
    this.state.data = state.data
  }

  override fun isEqualTo(element: PackagingElement<*>): Boolean = (element as? MyCompositeWorkspacePackagingElement)?.state?.data == state.data

  override fun getName(): String = state.name

  override fun rename(newName: String) {
    state.name = newName
  }

  override fun createPresentation(context: ArtifactEditorContext): PackagingElementPresentation {
    throw UnsupportedOperationException()
  }

  override fun toString(): String {
    return "MyCompositeWorkspacePackagingElement(state=$state)"
  }
}

data class MyCompositeWorkspacePackagingElementState(var data: String = "", var name: String = "")

object MyCompositeWorkspacePackagingElementType : PackagingElementType<MyCompositeWorkspacePackagingElement>("Composite-custom-element", Supplier { "Composite Custom Element" }) {
  override fun canCreate(context: ArtifactEditorContext, artifact: Artifact): Boolean = true

  override fun chooseAndCreate(context: ArtifactEditorContext,
                               artifact: Artifact,
                               parent: CompositePackagingElement<*>): MutableList<out PackagingElement<*>> {
    throw UnsupportedOperationException()
  }

  override fun createEmpty(project: Project): MyCompositeWorkspacePackagingElement {
    return MyCompositeWorkspacePackagingElement()
  }
}

internal val customArtifactTypes: List<ArtifactType> = List(10) {
  object : ArtifactType("myArtifactType-$it", Supplier{ "myArtifactType-$it" }) {

    override fun getIcon(): Icon = EmptyIcon.ICON_16

    override fun getDefaultPathFor(kind: PackagingElementOutputKind): String = ""

    override fun createRootElement(artifactName: String): CompositePackagingElement<*> {
      return PackagingElementFactory.getInstance().createArtifactRootElement()
    }
  }
}

internal val allArtifactTypes = customArtifactTypes + PlainArtifactType.getInstance()
