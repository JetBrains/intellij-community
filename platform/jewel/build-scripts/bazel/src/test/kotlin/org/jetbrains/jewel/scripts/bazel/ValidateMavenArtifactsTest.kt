package org.jetbrains.jewel.scripts.bazel

import com.github.ajalt.clikt.core.PrintMessage
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

class ValidateMavenArtifactsTest {
    private val fakeRunner = FakeCommandRunner { CmdResult.Success("") }
    private val command = ValidateMavenArtifactsCommand(fakeRunner)
    private val tmpDir = createSafeTempDir("validate-maven-artifacts-test")
    private val basicPom = """
      <?xml version="1.0" encoding="UTF-8"?>
      <project>
          <groupId>org.jetbrains.jewel</groupId>
          <artifactId>jewel-ui</artifactId>
          <version>1.0.0</version>
          <dependencies>
              <dependency>
                  <groupId>org.jetbrains.kotlin</groupId>
                  <artifactId>kotlin-stdlib</artifactId>
              </dependency>
          </dependencies>
      </project>
    """.trimIndent()
    private fun createPomFile(content: String = basicPom, name: String = "test.pom") = tmpDir.resolve(name).also { 
        it.writeText(content)
    }
    
    @After
    fun tearDown() {
        tmpDir.deleteRecursively()
    }

    @Test
    fun `getArtifactNameFromPom with groupId and artifactId should not throw an error`() {
        val result = command.getArtifactNameFromPom(createPomFile())

        assertEquals("org.jetbrains.jewel:jewel-ui", result)
    }

    @Test
    fun `getArtifactNameFromPom with missing groupId throws`() {
        val pomFileWithoutGroupId = createPomFile(
            content = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <artifactId>wheres-my-group-id</artifactId>
                </project>
            """.trimIndent()
        )

        assertFailsWith<PrintMessage> { 
            command.getArtifactNameFromPom(pomFileWithoutGroupId)
        }
    }

    @Test
    fun `getArtifactNameFromPom with missing artifactId throws`() {
        val pomFileWithoutArtifactId = createPomFile(
            content = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <groupId>wheres-my-artifact-id</groupId>
                </project>
            """.trimIndent()
        )

        assertFailsWith<PrintMessage> {
            command.getArtifactNameFromPom(pomFileWithoutArtifactId)
        }
    }
    
    @Test
    fun `getDependenciesFromPom with one dependency assert expected result`() {
        val result = command.getDependenciesFromPom(createPomFile())
        
        val expected = setOf("org.jetbrains.kotlin:kotlin-stdlib")
        
        assertEquals(expected, result)
    }

    @Test
    fun `getDependenciesFromPom skips dependency with missing groupId`() {
        val unresolvedDependencyPom = createPomFile(
            content = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <groupId>org.jetbrains.jewel</groupId>
                    <artifactId>jewel-ui</artifactId>
                    <dependencies>
                        <dependency>
                            <artifactId>incomplete-dependency</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.jetbrains.kotlin</groupId>
                            <artifactId>kotlin-stdlib</artifactId>
                        </dependency>
                    </dependencies>
                </project>
            """.trimIndent()
        )
        val result = command.getDependenciesFromPom(unresolvedDependencyPom)

        val expected = setOf("org.jetbrains.kotlin:kotlin-stdlib")

        assertEquals(expected, result)
    }

    @Test
    fun `getDependenciesFromPom skips dependency with missing artifactId`() {
        val unresolvedDependencyPom = createPomFile(
            content = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <groupId>org.jetbrains.jewel</groupId>
                    <artifactId>jewel-ui</artifactId>
                    <dependencies>
                        <dependency>
                            <groupId>incomplete-dependency</groupId>
                        </dependency>
                        <dependency>
                            <groupId>org.jetbrains.kotlin</groupId>
                            <artifactId>kotlin-stdlib</artifactId>
                        </dependency>
                    </dependencies>
                </project>
            """.trimIndent()
        )
        val result = command.getDependenciesFromPom(unresolvedDependencyPom)

        val expected = setOf("org.jetbrains.kotlin:kotlin-stdlib")

        assertEquals(expected, result)
    }

    @Test
    fun `getDependenciesFromPom with no dependencies returns empty set`() {
        val pomWithNoDeps = createPomFile(
            content = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <groupId>org.jetbrains.jewel</groupId>
                    <artifactId>jewel-ui</artifactId>
                </project>
            """.trimIndent()
        )

        val result = command.getDependenciesFromPom(pomWithNoDeps)

        assertEquals(emptySet(), result)
    }

    @Test
    fun `getVersionFromPom returns version string`() {
        val result = command.getVersionFromPom(createPomFile())

        assertEquals("1.0.0", result)
    }

    @Test
    fun `getVersionFromPom with missing version throws`() {
        val pomWithoutVersion = createPomFile(
            content = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <groupId>org.jetbrains.jewel</groupId>
                    <artifactId>jewel-ui</artifactId>
                </project>
            """.trimIndent()
        )

        assertFailsWith<PrintMessage> {
            command.getVersionFromPom(pomWithoutVersion)
        }
    }

    @Test
    fun `crossValidateDependencies with identical deps returns false`() {
        val deps = setOf("org.jetbrains.kotlin:kotlin-stdlib", "org.jetbrains.compose:compose-ui")

        val result = command.crossValidateDependencies(
            "org.jetbrains.jewel:jewel-ui",
            File("branch1.pom"),
            File("branch2.pom"),
            deps,
            deps,
        )

        assertFalse(result)
    }

    @Test
    fun `crossValidateDependencies with differing deps returns true`() {
        val deps1 = setOf("org.jetbrains.kotlin:kotlin-stdlib", "org.jetbrains.compose:compose-ui")
        val deps2 = setOf("org.jetbrains.kotlin:kotlin-stdlib")

        val result = command.crossValidateDependencies(
            "org.jetbrains.jewel:jewel-ui",
            File("branch1.pom"),
            File("branch2.pom"),
            deps1,
            deps2,
        )

        assertTrue(result)
    }

    @Test
    fun `crossValidateDependencies with both empty deps returns false`() {
        val result = command.crossValidateDependencies(
            "org.jetbrains.jewel:jewel-ui",
            File("branch1.pom"),
            File("branch2.pom"),
            emptySet(),
            emptySet(),
        )

        assertFalse(result)
    }

    @Test
    fun `cleanup reverts local changes and returns to the original branch`() = runTest {
        val artifactsDir = tmpDir.resolve("cleanup-git-only")

        command.cleanup(tmpDir, "main", artifactsDir, preserveTemp = true)

        assertEquals(listOf("git reset --hard", "git checkout main"), fakeRunner.calls)
    }

    @Test
    fun `cleanup deletes an existing artifacts directory when preserveTemp is false`() = runTest {
        val commandWithFakeDirectoryCheck = ValidateMavenArtifactsCommand(fakeRunner, isDirectory = { true })
        val artifactsDir = tmpDir.resolve("cleanup-target").also { it.mkdirs() }
        File(artifactsDir, "dummy.txt").writeText("x")

        commandWithFakeDirectoryCheck.cleanup(tmpDir, "main", artifactsDir, preserveTemp = false)

        assertFalse(artifactsDir.exists())
    }

    @Test
    fun `cleanup preserves the artifacts directory when preserveTemp is true`() = runTest {
        val commandWithFakeDirectoryCheck = ValidateMavenArtifactsCommand(fakeRunner, isDirectory = { true })
        val artifactsDir = tmpDir.resolve("preserved-target").also { it.mkdirs() }
        File(artifactsDir, "dummy.txt").writeText("x")

        commandWithFakeDirectoryCheck.cleanup(tmpDir, "main", artifactsDir, preserveTemp = true)

        assertTrue(artifactsDir.exists())
    }

    @Test
    fun `cleanup does not attempt deletion when isDirectory reports false, even if the real directory exists`() =
        runTest {
            val commandWithFakeDirectoryCheck = ValidateMavenArtifactsCommand(fakeRunner, isDirectory = { false })
            val artifactsDir = tmpDir.resolve("not-recognized-as-directory").also { it.mkdirs() }
            File(artifactsDir, "dummy.txt").writeText("x")

            commandWithFakeDirectoryCheck.cleanup(tmpDir, "main", artifactsDir, preserveTemp = false)

            // The real directory is untouched because isDirectory (the injected seam) said it wasn't one.
            assertTrue(artifactsDir.exists())
        }
}
