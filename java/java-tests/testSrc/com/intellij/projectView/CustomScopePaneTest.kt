// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectView

import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.impl.ProjectViewState
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.search.scope.packageSet.FilePatternPackageSet
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import com.intellij.psi.search.scope.packageSet.PackageSet
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.io.directoryContent
import com.intellij.util.io.generateInVirtualTempDir

class CustomScopePaneTest : AbstractProjectViewTest() {
  override fun tearDown() {
    NamedScopeManager.getInstance(project).removeAllSets()
    super.tearDown()
  }

  private fun allowed(any: Any?): Boolean {
    val node = any as? ProjectViewNode<*> ?: return true
    val file = node.virtualFile ?: return true
    return ProjectFileIndex.getInstance(project).isInContent(file)
  }

  private fun selectNewFilePattern(filePattern: String) {
    selectNewCustomScope(filePattern, FilePatternPackageSet(null, filePattern))
  }

  private fun selectNewCustomScope(id: String, packages: PackageSet) {
    selectProjectFilesPane()
    val scope = NamedScope(id, packages)
    NamedScopeManager.getInstance(project).addScope(scope)
    PlatformTestUtil.waitForAlarm(20)
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
    PsiTestUtil.addSourceRoot(module, root.findChild("src")!!)
  }


  fun `test default selection via IDE_VIEW`() {
    selectProjectFilesPane()
    with(ProjectViewState.getInstance(project)) {
      showModules = false
    }
    prepareProjectContent123()
    val test = createTreeTest().withSelection().setFilter { allowed(it.lastPathComponent) }

    val class3 = javaFacade.findClass("package1.package2.package3.Test3")!!
    LangDataKeys.IDE_VIEW.getData(currentPane)?.selectElement(class3)
    test.assertStructure("   -src\n" +
                         "    -package1\n" +
                         "     -package2\n" +
                         "      -package3\n" +
                         "       [Test3]\n" +
                         "     Test1\n" +
                         "    Test\n")
  }


  fun `test default scope with compact packages`() {
    selectProjectFilesPane()
    with(ProjectViewState.getInstance(project)) {
      hideEmptyMiddlePackages = true
      compactDirectories = true
      showModules = false
    }
    prepareProjectContent123()
    val test = createTreeTest().setFilter { allowed(it.lastPathComponent) }

    val class1 = javaFacade.findClass("package1.Test1")!!
    val class3 = javaFacade.findClass("package1.package2.package3.Test3")!!
    selectElement(class3)
    test.assertStructure("  -directory-by-spec/src\n" +
                         "   -package1\n" +
                         "    -package2.package3\n" +
                         "     Test3\n" +
                         "    Test1\n" +
                         "   Test\n")

    deleteElement(class1)
    selectElement(class3)
    test.assertStructure("  -directory-by-spec/src\n" +
                         "   -package1.package2.package3\n" +
                         "    Test3\n" +
                         "   Test\n")

    val class111 = javaFacade.findClass("Test")!!
    renameElement(class111, "Test111")
    test.assertStructure("  -directory-by-spec/src\n" +
                         "   -package1.package2.package3\n" +
                         "    Test3\n" +
                         "   Test111\n")

    val package2 = javaFacade.findPackage("package1.package2")!!
    val classNew = JavaDirectoryService.getInstance().createClass(package2.directories[0], "Class")
    selectElement(classNew)
    test.assertStructure("  -directory-by-spec/src\n" +
                         "   -package1.package2\n" +
                         "    +package3\n" +
                         "    Class\n" +
                         "   Test111\n")

    projectView.setFlattenPackages(currentPane.id, true)
    selectElement(classNew)
    test.assertStructure("  -directory-by-spec/src\n" +
                         "   -package1.package2\n" +
                         "    Class\n" +
                         "   +package1.package2.package3\n" +
                         "   Test111\n")
  }


  fun `test default scope with flatten packages`() {
    selectProjectFilesPane()
    with(ProjectViewState.getInstance(project)) {
      hideEmptyMiddlePackages = true
      flattenPackages = true
      showModules = false
    }
    prepareProjectContent123()
    val test = createTreeTest().setFilter { allowed(it.lastPathComponent) }

    val class1 = javaFacade.findClass("package1.Test1")!!
    val class3 = javaFacade.findClass("package1.package2.package3.Test3")!!
    selectElement(class3)
    test.assertStructure("   -src\n" +
                         "    +package1\n" +
                         "    -package1.package2.package3\n" +
                         "     Test3\n" +
                         "    Test\n")

    deleteElement(class1)
    selectElement(class3)
    test.assertStructure("   -src\n" +
                         "    -package1.package2.package3\n" +
                         "     Test3\n" +
                         "    Test\n")

    val class111 = javaFacade.findClass("Test")!!
    renameElement(class111, "Test111")
    test.assertStructure("   -src\n" +
                         "    -package1.package2.package3\n" +
                         "     Test3\n" +
                         "    Test111\n")

    val package3 = javaFacade.findPackage("package1.package2.package3")!!
    val classNew = JavaDirectoryService.getInstance().createClass(package3.directories[0], "Class")
    selectElement(classNew)
    test.assertStructure("   -src\n" +
                         "    -package1.package2.package3\n" +
                         "     Class\n" +
                         "     Test3\n" +
                         "    Test111\n")

    projectView.setHideEmptyPackages(currentPane.id, false)
    selectElement(classNew)
    test.assertStructure("   -src\n" +
                         "    package1\n" +
                         "    package1.package2\n" +
                         "    -package1.package2.package3\n" +
                         "     Class\n" +
                         "     Test3\n" +
                         "    Test111\n")
  }


  fun `test custom scope with compact packages`() {
    selectNewFilePattern("*/Test3*")
    with(ProjectViewState.getInstance(project)) {
      compactDirectories = true
      showModules = false
    }
    prepareProjectContent123()
    val test = createTreeTest().setFilter { allowed(it.lastPathComponent) }

    val class3 = javaFacade.findClass("package1.package2.package3.Test3")!!
    selectElement(class3)
    test.assertStructure("  -directory-by-spec/src\n" +
                         "   -package1.package2.package3\n" +
                         "    Test3\n")

    renameElement(class3, "ATest3") //exclude from scope
    test.assertStructure("")

    renameElement(class3, "Test3") //restore in scope
    selectElement(class3)
    test.assertStructure("  -directory-by-spec/src\n" +
                         "   -package1.package2.package3\n" +
                         "    Test3\n")

    movePackageToPackage("package1.package2.package3", "package1")
    selectElement(class3)
    test.assertStructure("  -directory-by-spec/src\n" +
                         "   -package1.package3\n" +
                         "    Test3\n")

    movePackageToPackage("package1.package3", "package1.package2")
    selectElement(class3)
    test.assertStructure("  -directory-by-spec/src\n" +
                         "   -package1.package2.package3\n" +
                         "    Test3\n")
  }


  fun `test custom scope without compact packages`() {
    selectNewFilePattern("*/Test3*")
    with(ProjectViewState.getInstance(project)) {
      showModules = false
    }
    prepareProjectContent123()
    val test = createTreeTest().setFilter { allowed(it.lastPathComponent) }

    val class3 = javaFacade.findClass("package1.package2.package3.Test3")!!
    selectElement(class3)
    test.assertStructure("   -src\n" +
                         "    -package1\n" +
                         "     -package2\n" +
                         "      -package3\n" +
                         "       Test3\n")

    renameElement(class3, "ATest3") //exclude from scope
    test.assertStructure("")

    renameElement(class3, "Test3") //restore in scope
    selectElement(class3)
    test.assertStructure("   -src\n" +
                         "    -package1\n" +
                         "     -package2\n" +
                         "      -package3\n" +
                         "       Test3\n")

    movePackageToPackage("package1.package2.package3", "package1")
    selectElement(class3)
    test.assertStructure("   -src\n" +
                         "    -package1\n" +
                         "     -package3\n" +
                         "      Test3\n")

    movePackageToPackage("package1.package3", "package1.package2")
    selectElement(class3)
    test.assertStructure("   -src\n" +
                         "    -package1\n" +
                         "     -package2\n" +
                         "      -package3\n" +
                         "       Test3\n")
  }


  fun `test custom scope structure`() {
    selectNewFilePattern("*/Test*")
    with(ProjectViewState.getInstance(project)) {
      showModules = false
    }
    prepareProjectContent123()
    val test = createTreeTest().setFilter { allowed(it.lastPathComponent) }

    val class1 = javaFacade.findClass("package1.Test1")!!
    val class3 = javaFacade.findClass("package1.package2.package3.Test3")!!
    selectElement(class3)
    test.assertStructure("   -src\n" +
                         "    -package1\n" +
                         "     -package2\n" +
                         "      -package3\n" +
                         "       Test3\n" +
                         "     Test1\n")

    deleteElement(class3)
    selectElement(class1)
    test.assertStructure("   -src\n" +
                         "    -package1\n" +
                         "     Test1\n")

    renameElement(class1, "Test111")
    test.assertStructure("   -src\n" +
                         "    -package1\n" +
                         "     Test111\n")

    val package1 = javaFacade.findPackage("package1")!!
    val classNew = JavaDirectoryService.getInstance().createClass(package1.directories[0], "Test222")
    selectElement(classNew)
    test.assertStructure("   -src\n" +
                         "    -package1\n" +
                         "     Test111\n" +
                         "     Test222\n")

    moveElementToPackage(classNew, "package1.package2.package3")
    selectElement(classNew)
    test.assertStructure("   -src\n" +
                         "    -package1\n" +
                         "     -package2\n" +
                         "      -package3\n" +
                         "       Test222\n" +
                         "     Test111\n")
  }
}
