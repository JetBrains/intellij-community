package com.intellij.externalSystem

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.getModule
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenDependencyUpdaterTest(mavenVersion: String, modelVersion: String) : MavenDependencyUpdaterTestBase(mavenVersion, modelVersion) {
  @Test
  fun testGetDependencies() = runBlocking {
    val dependencies = myModifierService!!.declaredDependencies(maven.getModule("project"))
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
    myModifierService!!.addDependency(maven.getModule("project"), UnifiedDependency("somegroup", "someartifact", "1.0", "compile"))
    assertFilesAsExpected()
  }

  @Test
  fun testAddDependencyToExistingList() {
    myModifierService!!.addDependency(maven.getModule("project"), UnifiedDependency("somegroup", "someartifact", "1.0", "compile"))
    assertFilesAsExpected()
  }

  @Test
  fun testRemoveDependency() {
    myModifierService!!.removeDependency(maven.getModule("project"), UnifiedDependency("somegroup", "someartifact", "1.0", "compile"))
    assertFilesAsExpected()
  }

  @Test
  fun testShouldAddDependencyToManagedTag() {
    myModifierService!!.addDependency(maven.getModule("m1"), UnifiedDependency("somegroup", "someartifact", "1.0", "compile"))
    assertFilesAsExpected()
  }


  @Test
  fun testShouldRemoveDependencyIfManaged() {
    myModifierService!!.removeDependency(maven.getModule("m1"), UnifiedDependency("somegroup", "someartifact", "1.0", "compile"))
    assertFilesAsExpected()
  }

  @Test
  fun testUpdateDependency() {
    myModifierService!!.updateDependency(maven.getModule("project"),
                                         UnifiedDependency("somegroup", "someartifact", "1.0", "compile"),
                                         UnifiedDependency("somegroup", "someartifact", "2.0", "test")
    )
    assertFilesAsExpected()
  }

  @Test
  fun testUpdateDependencyNoScope() {
    myModifierService!!.updateDependency(maven.getModule("project"),
                                         UnifiedDependency("somegroup", "someartifact", "1.0", null),
                                         UnifiedDependency("somegroup", "someartifact", "2.0", null)
    )
    assertFilesAsExpected()
  }

  @Test
  fun testUpdateDependencyRemoveScope() {
    myModifierService!!.updateDependency(maven.getModule("project"),
                                         UnifiedDependency("somegroup", "someartifact", "1.0", "compile"),
                                         UnifiedDependency("somegroup", "someartifact", "2.0", null)
    )
    assertFilesAsExpected()
  }

  @Test
  fun testUpdateManagedDependency() {
    myModifierService!!.updateDependency(maven.getModule("m1"),
                                         UnifiedDependency("somegroup", "someartifact", "1.0", "compile"),
                                         UnifiedDependency("somegroup", "someartifact", "2.0", "test")
    )
    assertFilesAsExpected()
  }

  @Test
  fun testUpdateManagedDependencyNoScope() {
    myModifierService!!.updateDependency(maven.getModule("m1"),
                                         UnifiedDependency("somegroup", "someartifact", "1.0", null),
                                         UnifiedDependency("somegroup", "someartifact", "2.0", null)
    )
    assertFilesAsExpected()
  }

  @Test
  fun testUpdateManagedDependencyRemoveScope() {
    myModifierService!!.updateDependency(maven.getModule("m1"),
                                         UnifiedDependency("somegroup", "someartifact", "1.0", "compile"),
                                         UnifiedDependency("somegroup", "someartifact", "2.0", null)
    )
    assertFilesAsExpected()
  }

  @Test
  fun testUpdateDependencyWithProperty() {
    myModifierService!!.updateDependency(maven.getModule("project"),
                                         UnifiedDependency("somegroup", "someartifact", "1.0", "compile"),
                                         UnifiedDependency("somegroup", "someartifact", "2.0", "test")
    )
    assertFilesAsExpected()
  }

  @Test
  fun testUpdateDependencyWithPropertyNoScope() {
    myModifierService!!.updateDependency(maven.getModule("project"),
                                         UnifiedDependency("somegroup", "someartifact", "1.0", null),
                                         UnifiedDependency("somegroup", "someartifact", "2.0", null)
    )
    assertFilesAsExpected()
  }

  @Test
  fun testUpdateDependencyWithPropertyRemoveScope() {
    myModifierService!!.updateDependency(maven.getModule("project"),
                                         UnifiedDependency("somegroup", "someartifact", "1.0", "compile"),
                                         UnifiedDependency("somegroup", "someartifact", "2.0", null)
    )
    assertFilesAsExpected()
  }

  @Test
  fun testAddRepository() {
    myModifierService!!.addRepository(maven.getModule("project"), UnifiedDependencyRepository("id", "name", "https://example.com"))
    assertFilesAsExpected()
  }

  @Test
  fun testRemoveRepository() {
    myModifierService!!.deleteRepository(maven.getModule("project"), UnifiedDependencyRepository("id", "name", "https://example.com"))
    assertFilesAsExpected()
  }
}
