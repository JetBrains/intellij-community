// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.JDOMUtil
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.jdom.Element
import org.jetbrains.intellij.build.classPath.DescriptorResolveContext
import org.jetbrains.intellij.build.classPath.XIncludeElementResolverImpl
import org.jetbrains.intellij.build.dev.DevDistDescriptorStage
import org.jetbrains.intellij.build.dev.DevDistDescriptorStages
import org.jetbrains.intellij.build.impl.PluginDescriptorPatchRequest
import org.jetbrains.intellij.build.impl.applyPluginDescriptorPatch
import org.jetbrains.intellij.build.impl.doPatchPluginXml
import org.junit.jupiter.api.Test

class PluginXmlPatcherTest {
  @Test
  fun setExistingVersion() = assertTransform(
    """
      <idea-plugin>
        <version>OLD</version>
        <name>CSS</name>
        <id>com.intellij.css</id>
      </idea-plugin>
    """.trimIndent(),
    """
    <idea-plugin>
      <version>x-plugin-version</version>
      <name>CSS</name>
      <id>com.intellij.css</id>
      <idea-version since-build="new-since" until-build="new-until" />
    </idea-plugin>
    """.trimIndent())

  @Test
  fun setMissingVersion() = assertTransform(
    """
      <idea-plugin>
        <name>CSS</name>
        <id>com.intellij.css</id>
      </idea-plugin>
    """.trimIndent(),
    """
    <idea-plugin>
      <name>CSS</name>
      <id>com.intellij.css</id>
      <version>x-plugin-version</version>
      <idea-version since-build="new-since" until-build="new-until" />
    </idea-plugin>
    """.trimIndent())

  @Test
  fun setMissingVersionNoAnchor() = assertTransform(
    """
      <idea-plugin></idea-plugin>
    """.trimIndent(),
    """
    <idea-plugin>
      <version>x-plugin-version</version>
      <idea-version since-build="new-since" until-build="new-until" />
    </idea-plugin>
    """.trimIndent())

  @Test
  fun setMissingSinceUntil() {
    assertTransform(
      """
        <idea-plugin>
          <name>CSS</name>
          <id>com.intellij.css</id>
        </idea-plugin>
      """.trimIndent(),
      """
      <idea-plugin>
        <name>CSS</name>
        <id>com.intellij.css</id>
        <version>x-plugin-version</version>
        <idea-version since-build="new-since" until-build="new-until" />
      </idea-plugin>
      """.trimIndent())
  }

  @Test
  fun setExistingSinceUntil() {
    assertTransform(
      """
        <idea-plugin>
          <name>CSS</name>
          <id>com.intellij.css</id>
          <idea-version since-build="qqq"/>
        </idea-plugin>
      """.trimIndent(),
      """
      <idea-plugin>
        <name>CSS</name>
        <id>com.intellij.css</id>
        <version>x-plugin-version</version>
        <idea-version since-build="new-since" until-build="new-until" />
      </idea-plugin>
      """.trimIndent())
  }

  @Test
  fun pluginDescriptorRemovedForBundledPlugins() = assertTransform(
    """
      <idea-plugin>
        <name>CSS</name>
        <id>com.intellij.css</id>
        <version>x-plugin-version</version>
        <idea-version since-build="new-since" until-build="new-until"/>
        
        <product-descriptor code="PDB" release-date="__DATE__" release-version="__VERSION__"/>
      </idea-plugin>
    """.trimIndent(),
    """
    <idea-plugin>
      <name>CSS</name>
      <id>com.intellij.css</id>
      <version>x-plugin-version</version>
      <idea-version since-build="new-since" until-build="new-until" />
    </idea-plugin>
    """.trimIndent(),
    toPublish = false
  )

  @Test
  fun releaseDateAndVersionSetForPublishedPlugins() = assertTransform(
    """
      <idea-plugin>
        <name>CSS</name>
        <id>com.intellij.css</id>
        <version>x-plugin-version</version>
        <idea-version since-build="new-since" until-build="new-until"/>

        <product-descriptor code="TEST" release-version="__VERSION__" eap="true"/>
      </idea-plugin>
    """.trimIndent(),
    """
    <idea-plugin>
      <name>CSS</name>
      <id>com.intellij.css</id>
      <version>x-plugin-version</version>
      <idea-version since-build="new-since" until-build="new-until" />
      <product-descriptor code="TEST" release-version="X-RELEASE-VERSION-X" release-date="X-RELEASE-DATE-X" />
    </idea-plugin>
    """.trimIndent(),
    toPublish = true,
    isEap = false
  )

  @Test
  fun eapSetInPublishedPlugins() = assertTransform(
    """
      <idea-plugin>
        <name>CSS</name>
        <id>com.intellij.css</id>
        <version>x-plugin-version</version>
        <idea-version since-build="new-since" until-build="new-until"/>

        <product-descriptor code="TEST" />
      </idea-plugin>
    """.trimIndent(),
    """
    <idea-plugin>
      <name>CSS</name>
      <id>com.intellij.css</id>
      <version>x-plugin-version</version>
      <idea-version since-build="new-since" until-build="new-until" />
      <product-descriptor code="TEST" eap="true" release-date="X-RELEASE-DATE-X" release-version="X-RELEASE-VERSION-X" />
    </idea-plugin>
    """.trimIndent(),
    toPublish = true,
    isEap = true
  )

  @Test
  fun doNotPatchDatabasePluginIfBundled() = assertTransform(
    """
      <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
        <name>Database Tools and SQL</name>
        <id>com.intellij.database</id>
        <product-descriptor code="PDB" release-date="__DATE__" release-version="__VERSION__"/>
        <description>
            <![CDATA[
              The Database Tools and SQL plugin for IntelliJ-based IDEs allows you to query, create, and manage databases and provides full SQL language support.
              <br><br>
              The plugin provides all the same features as <a href="https://www.jetbrains.com/datagrip/">DataGrip</a>, the standalone JetBrains IDE for databases.
              <br><br>
            ]]>
        </description>
      </idea-plugin>
    """.trimIndent(),
    """
<idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
  <name>Database Tools and SQL</name>
  <id>com.intellij.database</id>
  <version>x-plugin-version</version>
  <idea-version since-build="new-since" until-build="new-until" />
  <description><![CDATA[The Database Tools and SQL plugin for IntelliJ-based IDEs allows you to query, create, and manage databases and provides full SQL language support.
        <br><br>
        The plugin provides all the same features as <a href="https://www.jetbrains.com/datagrip/">DataGrip</a>, the standalone JetBrains IDE for databases.
        <br><br>]]></description>
</idea-plugin>
    """.trimIndent(),
    toPublish = false,
  )

  @Test
  fun retainProductDescriptorForBundledPluginFlag() = assertTransform(
    """
      <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
        <id>com</id>
        <product-descriptor code="PCWMP" release-date="__DATE__" release-version="__VERSION__" optional="true"/>
      </idea-plugin>
    """.trimIndent(),
    """
  <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
    <id>com</id>
    <version>x-plugin-version</version>
    <idea-version since-build="new-since" until-build="new-until" />
    <product-descriptor code="PCWMP" release-date="X-RELEASE-DATE-X" release-version="X-RELEASE-VERSION-X" optional="true" />
  </idea-plugin>
    """.trimIndent(),
    retainProductDescriptorForBundledPlugin = true,
    toPublish = false,
  )

  /**
   * The shared body must run without a build context, a plugin layout or a platform layout. The resolver here refuses
   * a module read, so a body that reached the JPS project model would fail rather than pass quietly.
   */
  @Test
  fun sharedBodyRunsWithNoProjectModel() {
    val embedded = ArrayList<String>()
    val patched = runBlocking {
      applyPluginDescriptorPatch(
        request = request(
          """
            <idea-plugin>
              <id>com.intellij.css</id>
              <content>
                <module name="intellij.css.backend" />
              </content>
            </idea-plugin>
          """.trimIndent()
        ),
        xIncludeResolver = XIncludeElementResolverImpl(searchPath = emptyList(), context = NoProjectModelContext),
        stages = null,
        embedContentModules = { rootElement -> embedded.addAll(contentModuleNames(rootElement)) },
        patchText = { "$it\n<!-- text patcher -->" },
      )
    }

    assertThat(embedded).containsExactly("intellij.css.backend")
    assertThat(patched).isEqualTo(
      """
      <idea-plugin>
        <id>com.intellij.css</id>
        <version>x-plugin-version</version>
        <idea-version since-build="new-since" until-build="new-until" />
        <content>
          <module name="intellij.css.backend" />
        </content>
      </idea-plugin>
      <!-- text patcher -->
      """.trimIndent()
    )
  }

  /**
   * The stage record is the vocabulary of the descriptor report, and it states that the steps are in the order they
   * run. This case holds that order for the body both producers share.
   */
  @Test
  fun sharedBodyRecordsEveryStageInOrder() {
    val stages = DevDistDescriptorStages()
    val source = "<idea-plugin>\n  <id>com.intellij.css</id>\n</idea-plugin>"
    val patched = runBlocking {
      applyPluginDescriptorPatch(
        request = request(source),
        xIncludeResolver = XIncludeElementResolverImpl(searchPath = emptyList(), context = NoProjectModelContext),
        stages = stages,
        embedContentModules = { },
        patchText = { it },
      )
    }

    val record = stages.toRecord(
      mainModule = "x-plugin-module-name",
      directoryName = "x-plugin-directory",
      mainJar = "x-plugin.jar",
      embedsContentModules = true,
    )
    assertThat(record.steps.map { it.stage }).containsExactly(*DevDistDescriptorStage.entries.toTypedArray())
    assertThat(record.source).isEqualTo(source)
    assertThat(record.patched).isEqualTo(patched)
  }

  private fun request(source: String): PluginDescriptorPatchRequest = PluginDescriptorPatchRequest(
    mainModule = "x-plugin-module-name",
    directoryName = "x-plugin-directory",
    mainJarName = "x-plugin.jar",
    sourceContent = source,
    rawPatchedContent = source,
    pluginVersion = "x-plugin-version",
    compatibleSinceUntil = Pair("new-since", "new-until"),
    releaseDate = "X-RELEASE-DATE-X",
    releaseVersion = "X-RELEASE-VERSION-X",
    toPublish = false,
    retainProductDescriptorForBundledPlugin = false,
    isEap = false,
    embedsContentModules = true,
  )

  private fun contentModuleNames(rootElement: Element): List<String> {
    return rootElement.getChildren("content").flatMap { content ->
      content.getChildren("module").mapNotNull { it.getAttributeValue("name") }
    }
  }

  private fun assertTransform(
    @Language("XML") before: String,
    @Language("XML") after: String,
    toPublish: Boolean = false,
    isEap: Boolean = false,
    retainProductDescriptorForBundledPlugin: Boolean = false,
  ) {
    val result = JDOMUtil.load(before)
    doPatchPluginXml(
      rootElement = result,
      pluginModuleName = "x-plugin-module-name",
      pluginVersion = "x-plugin-version",
      releaseDate = "X-RELEASE-DATE-X",
      releaseVersion = "X-RELEASE-VERSION-X",
      compatibleSinceUntil = Pair("new-since", "new-until"),
      toPublish = toPublish,
      retainProductDescriptorForBundledPlugin = retainProductDescriptorForBundledPlugin,
      isEap = isEap,
    )
    assertThat(JDOMUtil.write(result)).isEqualTo(after)
  }
}

private object NoProjectModelContext : DescriptorResolveContext {
  override val outputProvider: ModuleOutputProvider
    get() = throw UnsupportedOperationException("the shared descriptor patch must not read the JPS project model")

  override val productPropertiesName: String
    get() = "PluginXmlPatcherTest"
}
