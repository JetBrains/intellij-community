package org.jetbrains.jewel.scripts.bazel

import java.io.File
import kotlin.test.assertEquals
import org.junit.Test

class DetektRunnerTest {
    @Test
    fun `parseOptInAnnotations extracts opt_in list values, deduped and sorted`() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8" standalone="no"?>
            <query>
              <rule class="kt_kotlinc_options" name="//platform/jewel/foundation:custom_foundation">
                <list name="opt_in">
                  <string value="org.jetbrains.jewel.foundation.ExperimentalJewelApi"/>
                  <string value="org.jetbrains.jewel.foundation.InternalJewelApi"/>
                </list>
              </rule>
              <rule class="kt_kotlinc_options" name="//platform/jewel/ui:custom_ui">
                <list name="opt_in">
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
    fun `parseOptInAnnotations returns an empty list when no opt_in list is present`() {
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
}
