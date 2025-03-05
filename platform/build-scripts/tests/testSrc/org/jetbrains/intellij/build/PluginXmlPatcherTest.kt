// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.JDOMUtil
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
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

  private fun assertTransform(
    @Language("XML") before: String,
    @Language("XML") after: String,
    toPublish: Boolean = false,
    isEap: Boolean = false,
    retainProductDescriptorForBundledPlugin: Boolean = false,
  ) {
    val result = doPatchPluginXml(
      rootElement = JDOMUtil.load(before),
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