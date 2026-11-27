package org.jetbrains.jewel.scripts.bazel

import java.io.File
import kotlin.test.assertEquals
import org.junit.Test

class DetektRunnerTest {
    @Test
    fun `parseJewelModuleDirs returns the folders of the Jewel modules, deduped and sorted`() {
        val xml =
            modulesXml(
                "platform/jewel/ui/intellij.platform.jewel.ui.iml",
                "platform/jewel/ui/intellij.platform.jewel.ui.tests.iml",
                "platform/core-api/intellij.platform.core.iml",
                "platform/jewel/markdown/core/intellij.platform.jewel.markdown.core.iml",
                "platform/jewelry/intellij.platform.jewelry.iml",
                "platform/jewel/foundation/intellij.platform.jewel.foundation.iml",
            )

        assertEquals(
            listOf("platform/jewel/foundation", "platform/jewel/markdown/core", "platform/jewel/ui"),
            parseJewelModuleDirs(xml),
        )
    }

    @Test
    fun `parseJewelModuleDirs returns an empty list when no Jewel module is registered`() {
        val xml = modulesXml("platform/core-api/intellij.platform.core.iml")

        assertEquals(emptyList(), parseJewelModuleDirs(xml))
    }

    @Test
    fun `parseOptInAnnotations extracts x_optin list values, deduped and sorted`() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8" standalone="no"?>
            <query>
              <rule class="kt_kotlinc_options" name="//platform/jewel/foundation:custom_foundation">
                <list name="x_optin">
                  <string value="org.jetbrains.jewel.foundation.ExperimentalJewelApi"/>
                  <string value="org.jetbrains.jewel.foundation.InternalJewelApi"/>
                </list>
              </rule>
              <rule class="kt_kotlinc_options" name="//platform/jewel/ui:custom_ui">
                <list name="x_optin">
                  <string value="org.jetbrains.jewel.foundation.InternalJewelApi"/>
                  <string value="androidx.compose.ui.ExperimentalComposeUiApi"/>
                </list>
                <list name="other_attr">
                  <string value="should_be_ignored"/>
                </list>
              </rule>
            </query>
            """
                .trimIndent()

        val result = parseOptInAnnotations(xml)

        assertEquals(
            listOf(
                "androidx.compose.ui.ExperimentalComposeUiApi",
                "org.jetbrains.jewel.foundation.ExperimentalJewelApi",
                "org.jetbrains.jewel.foundation.InternalJewelApi",
            ),
            result,
        )
    }

    @Test
    fun `parseOptInAnnotations returns an empty list when no x_optin list is present`() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8" standalone="no"?>
            <query>
              <rule class="kt_kotlinc_options" name="//platform/jewel/foundation:custom_foundation">
                <list name="other_attr">
                  <string value="irrelevant"/>
                </list>
              </rule>
            </query>
            """
                .trimIndent()

        assertEquals(emptyList(), parseOptInAnnotations(xml))
    }

    @Test
    fun `buildDetektArgs assembles input, config, classpath, passthrough args, and opt-in suffix in order`() {
        val workspaceDir = File("/workspace")
        val scope =
            JewelBazelScope(
                inputDirs = listOf("/workspace/platform/jewel/foundation", "/workspace/platform/jewel/ui"),
                classpath = "/foo/bar.jar:/foo/baz.jar",
                optInAnnotations = listOf("org.jetbrains.jewel.foundation.InternalJewelApi"),
            )

        val result = buildDetektArgs(workspaceDir, scope, arrayOf("--debug"))

        assertEquals(
            listOf(
                "--input",
                "/workspace/platform/jewel/foundation${File.pathSeparator}/workspace/platform/jewel/ui",
                "--config",
                "/workspace/platform/jewel/detekt.yml",
                "--excludes",
                "**/generated/**,**/build/**,**/buildSrc/**,**/*.kts,**/test/resources/**",
                "--build-upon-default-config",
                "--analysis-mode",
                "full",
                "--classpath",
                "/foo/bar.jar:/foo/baz.jar",
                "--debug",
                "-opt-in",
                "org.jetbrains.jewel.foundation.InternalJewelApi",
            ),
            result.toList(),
        )
    }

    @Test
    fun `buildDetektArgs joins multiple opt-in annotations with commas`() {
        val workspaceDir = File("/workspace")
        val scope =
            JewelBazelScope(
                inputDirs = listOf("/workspace/platform/jewel/foundation"),
                classpath = "/foo/bar.jar",
                optInAnnotations =
                    listOf(
                        "androidx.compose.ui.ExperimentalComposeUiApi",
                        "org.jetbrains.jewel.foundation.InternalJewelApi",
                    ),
            )

        val result = buildDetektArgs(workspaceDir, scope, emptyArray())

        assertEquals(
            "androidx.compose.ui.ExperimentalComposeUiApi,org.jetbrains.jewel.foundation.InternalJewelApi",
            result.last(),
        )
    }

    private fun modulesXml(vararg imlPaths: String): String {
        val projectDir = "\$PROJECT_DIR\$"
        val modules =
            imlPaths.joinToString("\n") { """<module fileurl="file://$projectDir/$it" filepath="$projectDir/$it" />""" }
        return """<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="ProjectModuleManager">
    <modules>
$modules
    </modules>
  </component>
</project>"""
    }
}
