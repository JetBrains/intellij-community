package com.intellij.externalSystem

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import org.junit.Test

class MavenDependencyUpdaterTest : MavenDependencyUpdaterTestBase() {
  @Test
  fun testGetDependencies() {
    val dependencies = myModifierService!!.declaredDependencies(getModule("project"))
    assertNotNull(dependencies)
    assertEquals(2, dependencies.size)

    val someArtifact = findDependencyTag("somegroup", "someartifact", "1.0")
    val another = findDependencyTag("anothergroup", "anotherArtifact", "2.0")

    assertEquals(UnifiedDependency("somegroup", "someartifact", "1.0", null), dependencies[0].unifiedDependency)
    assertEquals(UnifiedDependency("anothergroup", "anotherArtifact", "2.0", null), dependencies[1].unifiedDependency)

    assertEquals(someArtifact, dependencies[0].psiElement)
    assertEquals(another, dependencies[1].psiElement)
  }

  @Test
  fun testAddDependency() {
    myModifierService!!.addDependency(getModule("project"), UnifiedDependency("somegroup", "someartifact", "1.0", "compile"))
    assertFilesAsExpected()
  }

  @Test
  fun testAddDependencyToExistingList() {
    myModifierService!!.addDependency(getModule("project"), UnifiedDependency("somegroup", "someartifact", "1.0", "compile"))
    assertFilesAsExpected()
  }

  @Test
  fun testRemoveDependency() {
    myModifierService!!.removeDependency(getModule("project"), UnifiedDependency("somegroup", "someartifact", "1.0", "compile"))
    assertFilesAsExpected()
  }

  @Test
  fun testShouldAddDependencyToManagedTag() {
    myModifierService!!.addDependency(getModule("m1"), UnifiedDependency("somegroup", "someartifact", "1.0", "compile"))
    assertFilesAsExpected()
  }


  @Test
  fun testShouldRemoveDependencyIfManaged() {
    myModifierService!!.removeDependency(getModule("m1"), UnifiedDependency("somegroup", "someartifact", "1.0", "compile"))
    assertFilesAsExpected()
  }

  @Test
  fun testUpdateDependency() {
    myModifierService!!.updateDependency(getModule("project"),
                                         UnifiedDependency("somegroup", "someartifact", "1.0", "compile"),
                                         UnifiedDependency("somegroup", "someartifact", "2.0", "test")
    )
    assertFilesAsExpected()
  }

  @Test
  fun testUpdateDependencyNoScope() {
    myModifierService!!.updateDependency(getModule("project"),
                                         UnifiedDependency("somegroup", "someartifact", "1.0", null),
                                         UnifiedDependency("somegroup", "someartifact", "2.0", null)
    )
    assertFilesAsExpected()
  }

  @Test
  fun testUpdateDependencyRemoveScope() {
    myModifierService!!.updateDependency(getModule("project"),
                                         UnifiedDependency("somegroup", "someartifact", "1.0", "compile"),
                                         UnifiedDependency("somegroup", "someartifact", "2.0", null)
    )
    assertFilesAsExpected()
  }

  @Test
  fun testUpdateManagedDependency() {
    myModifierService!!.updateDependency(getModule("m1"),
                                         UnifiedDependency("somegroup", "someartifact", "1.0", "compile"),
                                         UnifiedDependency("somegroup", "someartifact", "2.0", "test")
    )
    assertFilesAsExpected()
  }

  @Test
  fun testUpdateManagedDependencyNoScope() {
    myModifierService!!.updateDependency(getModule("m1"),
                                         UnifiedDependency("somegroup", "someartifact", "1.0", null),
                                         UnifiedDependency("somegroup", "someartifact", "2.0", null)
    )
    assertFilesAsExpected()
  }

  @Test
  fun testUpdateManagedDependencyRemoveScope() {
    myModifierService!!.updateDependency(getModule("m1"),
                                         UnifiedDependency("somegroup", "someartifact", "1.0", "compile"),
                                         UnifiedDependency("somegroup", "someartifact", "2.0", null)
    )
    assertFilesAsExpected()
  }

  @Test
  fun testUpdateDependencyWithProperty() {
    myModifierService!!.updateDependency(getModule("project"),
                                         UnifiedDependency("somegroup", "someartifact", "1.0", "compile"),
                                         UnifiedDependency("somegroup", "someartifact", "2.0", "test")
    )
    assertFilesAsExpected()
  }

  @Test
  fun testUpdateDependencyWithPropertyNoScope() {
    myModifierService!!.updateDependency(getModule("project"),
                                         UnifiedDependency("somegroup", "someartifact", "1.0", null),
                                         UnifiedDependency("somegroup", "someartifact", "2.0", null)
    )
    assertFilesAsExpected()
  }

  @Test
  fun testUpdateDependencyWithPropertyRemoveScope() {
    myModifierService!!.updateDependency(getModule("project"),
                                         UnifiedDependency("somegroup", "someartifact", "1.0", "compile"),
                                         UnifiedDependency("somegroup", "someartifact", "2.0", null)
    )
    assertFilesAsExpected()
  }

  @Test
  fun testAddRepository() {
    myModifierService!!.addRepository(getModule("project"), UnifiedDependencyRepository("id", "name", "https://example.com"))
    assertFilesAsExpected()
  }

  @Test
  fun testRemoveRepository() {
    myModifierService!!.deleteRepository(getModule("project"), UnifiedDependencyRepository("id", "name", "https://example.com"))
    assertFilesAsExpected()
  }
}
