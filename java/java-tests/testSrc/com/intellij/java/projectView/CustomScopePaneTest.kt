// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.projectView

import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.impl.ProjectViewState
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.search.scope.packageSet.FilePatternPackageSet
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import com.intellij.psi.search.scope.packageSet.PackageSet
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.io.directoryContent
import com.intellij.util.io.generateInVirtualTempDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

@TestApplication
class CustomScopePaneTest {
  private val fixture by projectViewPaneFixture(iconRequired = true)

  private val contentRootPresentation: String
    get() = FileUtil.getLocationRelativeToUserHome(ModuleRootManager.getInstance(fixture.module).contentRoots[0].path)

  @AfterEach
  fun tearDown() {
    NamedScopeManager.getInstance(fixture.project).removeAllSets()
  }

  private fun allowed(any: Any?): Boolean {
    val node = any as? ProjectViewNode<*> ?: return true
    val file = node.virtualFile ?: return true
    return ProjectFileIndex.getInstance(fixture.project).isInContent(file)
  }

  private fun selectNewFilePattern(filePattern: String) {
    selectNewCustomScope(filePattern, FilePatternPackageSet(null, filePattern))
  }

  private fun selectNewCustomScope(id: String, packages: PackageSet): Unit = with(fixture) {
    selectProjectFilesPane()
    val scope = NamedScope(id, packages)
    NamedScopeManager.getInstance(project).addScope(scope)
    timeoutRunBlocking {
      withContext(Dispatchers.EDT) {
        PlatformTestUtil.waitForAlarm(20)
      }
    }
    selectScopeViewPane(scope)
  }

  private fun prepareProjectContent123() {
    val root = directoryContent {
      dir("src") {
        dir("package1") {
          dir("package2") {
            dir("package3") {
              file("Test3.java", """
                package package1.package2.package3;
                class Test3 {
                }""")
            }
          }
          file("Test1.java", """
            package package1;
            class Test1 {
            }""")
        }
        file("Test.java", """
            class Test {
            }""")
      }
    }.generateInVirtualTempDir()
    PsiTestUtil.addSourceRoot(fixture.module, root.findChild("src")!!)
  }


  @Test
  fun `test default selection via IDE_VIEW`(): Unit = with(fixture) {
    selectProjectFilesPane()
    with(ProjectViewState.getInstance(project)) {
      showModules = false
    }
    prepareProjectContent123()
    val test = createTreeTest().withSelection().setFilter { allowed(it.lastPathComponent) }

    val class3 = findClass("package1.package2.package3.Test3")
    timeoutRunBlocking {
      withContext(Dispatchers.EDT) {
        LangDataKeys.IDE_VIEW.getData(currentPane)?.selectElement(class3)
      }
    }
    assertStructure(test, " -" + contentRootPresentation + "\n" +
                          "  -package1\n" +
                          "   -package2\n" +
                          "    -package3\n" +
                          "     [Test3]\n" +
                          "   Test1\n" +
                          "  Test\n")
  }


  @Test
  fun `test default scope with compact packages`(): Unit = with(fixture) {
    selectProjectFilesPane()
    with(ProjectViewState.getInstance(project)) {
      hideEmptyMiddlePackages = true
      compactDirectories = true
      showModules = false
    }
    prepareProjectContent123()
    val test = createTreeTest().setFilter { allowed(it.lastPathComponent) }

    val class1 = findClass("package1.Test1")
    val class3 = findClass("package1.package2.package3.Test3")
    selectElement(class3)
    assertStructure(test, " -" + contentRootPresentation + "\n" +
                          "  -package1\n" +
                          "   -package2.package3\n" +
                          "    Test3\n" +
                          "   Test1\n" +
                          "  Test\n")

    deleteElement(class1)
    selectElement(class3)
    assertStructure(test, " -" + contentRootPresentation + "\n" +
                          "  -package1.package2.package3\n" +
                          "   Test3\n" +
                          "  Test\n")

    val class111 = findClass("Test")
    renameElement(class111, "Test111")
    assertStructure(test, " -" + contentRootPresentation + "\n" +
                          "  -package1.package2.package3\n" +
                          "   Test3\n" +
                          "  Test111\n")

    val classNew = createClassInPackage("package1.package2", "Class")
    selectElement(classNew)
    assertStructure(test, " -" + contentRootPresentation + "\n" +
                          "  -package1.package2\n" +
                          "   +package3\n" +
                          "   Class\n" +
                          "  Test111\n")

    timeoutRunBlocking {

      withContext(Dispatchers.EDT) {

        projectView.setFlattenPackages(currentPane.id, true)

      }

    }
    selectElement(classNew)
    assertStructure(test, " -" + contentRootPresentation + "\n" +
                          "  -package1.package2\n" +
                          "   Class\n" +
                          "  +package1.package2.package3\n" +
                          "  Test111\n")
  }


  @Test
  fun `test default scope with flatten packages`(): Unit = with(fixture) {
    selectProjectFilesPane()
    with(ProjectViewState.getInstance(project)) {
      hideEmptyMiddlePackages = true
      flattenPackages = true
      showModules = false
    }
    prepareProjectContent123()
    val test = createTreeTest().setFilter { allowed(it.lastPathComponent) }

    val class1 = findClass("package1.Test1")
    val class3 = findClass("package1.package2.package3.Test3")
    selectElement(class3)
    assertStructure(test, " -" + contentRootPresentation + "\n" +
                          "  +package1\n" +
                          "  -package1.package2.package3\n" +
                          "   Test3\n" +
                          "  Test\n")

    deleteElement(class1)
    selectElement(class3)
    assertStructure(test, " -" + contentRootPresentation + "\n" +
                          "  -package1.package2.package3\n" +
                          "   Test3\n" +
                          "  Test\n")

    val class111 = findClass("Test")
    renameElement(class111, "Test111")
    assertStructure(test, " -" + contentRootPresentation + "\n" +
                          "  -package1.package2.package3\n" +
                          "   Test3\n" +
                          "  Test111\n")

    val classNew = createClassInPackage("package1.package2.package3", "Class")
    selectElement(classNew)
    assertStructure(test, " -" + contentRootPresentation + "\n" +
                          "  -package1.package2.package3\n" +
                          "   Class\n" +
                          "   Test3\n" +
                          "  Test111\n")

    timeoutRunBlocking {

      withContext(Dispatchers.EDT) {

        projectView.setHideEmptyPackages(currentPane.id, false)

      }

    }
    selectElement(classNew)
    assertStructure(test, " -" + contentRootPresentation + "\n" +
                          "  package1\n" +
                          "  package1.package2\n" +
                          "  -package1.package2.package3\n" +
                          "   Class\n" +
                          "   Test3\n" +
                          "  Test111\n")
  }


  @Test
  fun `test custom scope with compact packages`(): Unit = with(fixture) {
    selectNewFilePattern("*/Test3*")
    with(ProjectViewState.getInstance(project)) {
      compactDirectories = true
      showModules = false
    }
    prepareProjectContent123()
    val test = createTreeTest().setFilter { allowed(it.lastPathComponent) }

    val class3 = findClass("package1.package2.package3.Test3")
    selectElement(class3)
    assertStructure(test, " -" + contentRootPresentation + "\n" +
                          "  -package1.package2.package3\n" +
                          "   Test3\n")

    renameElement(class3, "ATest3") //exclude from scope
    assertStructure(test, "")

    renameElement(class3, "Test3") //restore in scope
    selectElement(class3)
    assertStructure(test, " -" + contentRootPresentation + "\n" +
                          "  -package1.package2.package3\n" +
                          "   Test3\n")

    movePackageToPackage("package1.package2.package3", "package1")
    selectElement(class3)
    assertStructure(test, " -" + contentRootPresentation + "\n" +
                          "  -package1.package3\n" +
                          "   Test3\n")

    movePackageToPackage("package1.package3", "package1.package2")
    selectElement(class3)
    assertStructure(test, " -" + contentRootPresentation + "\n" +
                          "  -package1.package2.package3\n" +
                          "   Test3\n")
  }


  @Test
  fun `test custom scope without compact packages`(): Unit = with(fixture) {
    selectNewFilePattern("*/Test3*")
    with(ProjectViewState.getInstance(project)) {
      showModules = false
    }
    prepareProjectContent123()
    val test = createTreeTest().setFilter { allowed(it.lastPathComponent) }

    val class3 = findClass("package1.package2.package3.Test3")
    selectElement(class3)
    assertStructure(test, " -" + contentRootPresentation + "\n" +
                          "  -package1\n" +
                          "   -package2\n" +
                          "    -package3\n" +
                          "     Test3\n")

    renameElement(class3, "ATest3") //exclude from scope
    assertStructure(test, "")

    renameElement(class3, "Test3") //restore in scope
    selectElement(class3)
    assertStructure(test, " -" + contentRootPresentation + "\n" +
                          "  -package1\n" +
                          "   -package2\n" +
                          "    -package3\n" +
                          "     Test3\n")

    movePackageToPackage("package1.package2.package3", "package1")
    selectElement(class3)
    assertStructure(test, " -" + contentRootPresentation + "\n" +
                          "  -package1\n" +
                          "   -package3\n" +
                          "    Test3\n")

    movePackageToPackage("package1.package3", "package1.package2")
    selectElement(class3)
    assertStructure(test, " -" + contentRootPresentation + "\n" +
                          "  -package1\n" +
                          "   -package2\n" +
                          "    -package3\n" +
                          "     Test3\n")
  }


  @Test
  fun `test custom scope structure`(): Unit = with(fixture) {
    selectNewFilePattern("*/Test*")
    with(ProjectViewState.getInstance(project)) {
      showModules = false
    }
    prepareProjectContent123()
    val test = createTreeTest().setFilter { allowed(it.lastPathComponent) }

    val class1 = findClass("package1.Test1")
    val class3 = findClass("package1.package2.package3.Test3")
    selectElement(class3)
    assertStructure(test, " -" + contentRootPresentation + "\n" +
                          "  -package1\n" +
                          "   -package2\n" +
                          "    -package3\n" +
                          "     Test3\n" +
                          "   Test1\n")

    deleteElement(class3)
    selectElement(class1)
    assertStructure(test, " -" + contentRootPresentation + "\n" +
                          "  -package1\n" +
                          "   Test1\n")

    renameElement(class1, "Test111")
    assertStructure(test, " -" + contentRootPresentation + "\n" +
                          "  -package1\n" +
                          "   Test111\n")

    val classNew = createClassInPackage("package1", "Test222")
    selectElement(classNew)
    assertStructure(test, " -" + contentRootPresentation + "\n" +
                          "  -package1\n" +
                          "   Test111\n" +
                          "   Test222\n")

    moveElementToPackage(classNew, "package1.package2.package3")
    selectElement(classNew)
    assertStructure(test, " -" + contentRootPresentation + "\n" +
                          "  -package1\n" +
                          "   -package2\n" +
                          "    -package3\n" +
                          "     Test222\n" +
                          "   Test111\n")
  }
}
