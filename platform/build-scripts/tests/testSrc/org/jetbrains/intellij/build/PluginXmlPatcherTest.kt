// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import de.pdark.decentxml.XMLParser
import org.assertj.core.api.Assertions.assertThat
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
        <idea-version since-build="new-since" until-build="new-until"/>
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
        <idea-version since-build="new-since" until-build="new-until"/>
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
        <idea-version since-build="new-since" until-build="new-until"/></idea-plugin>
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
          <idea-version since-build="new-since" until-build="new-until"/>
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
          <idea-version since-build="new-since" until-build="new-until"/>
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
        <idea-version since-build="new-since" until-build="new-until"/>
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

        <product-descriptor code="PDB" release-version="__VERSION__" eap="true"/>
      </idea-plugin>
    """.trimIndent(),
    """
      <idea-plugin>
        <name>CSS</name>
        <id>com.intellij.css</id>
        <version>x-plugin-version</version>
        <idea-version since-build="new-since" until-build="new-until"/>

        <product-descriptor code="PDB" release-version="X-RELEASE-VERSION-X" release-date="X-RELEASE-DATE-X"/>
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

        <product-descriptor code="PDB" />
      </idea-plugin>
    """.trimIndent(),
    """
      <idea-plugin>
        <name>CSS</name>
        <id>com.intellij.css</id>
        <version>x-plugin-version</version>
        <idea-version since-build="new-since" until-build="new-until"/>

        <product-descriptor code="PDB" eap="true" release-date="X-RELEASE-DATE-X" release-version="X-RELEASE-VERSION-X" />
      </idea-plugin>
    """.trimIndent(),
    toPublish = true,
    isEap = true
  )

  @Test
  fun patchDatabasePluginInWebStorm() = assertTransform(
    """
      <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
        <name>Database Tools and SQL</name>
        <id>com.intellij.database</id>
        <product-descriptor code="PDB" release-date="__DATE__" release-version="__VERSION__"/>
        <description>
            <![CDATA[
              xxx for IntelliJ-based IDEs provides
            ]]>
        </description>
      </idea-plugin>
    """.trimIndent(),
    """
      <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
        <name>Database Tools and SQL for WebStorm</name>
        <id>com.intellij.database</id>
        <version>x-plugin-version</version>
        <idea-version since-build="new-since" until-build="new-until"/>
        <product-descriptor code="PDB" release-date="20230327" release-version="X-RELEASE-VERSION-X"/>
        <description>
            <![CDATA[
              xxx for WebStorm provides
            ]]>
        </description>
      </idea-plugin>
    """.trimIndent(),
    productName = "WebStorm",
    toPublish = true,
  )

  @Test
  fun doNotPatchDatabasePluginInGenericProduct() = assertTransform(
    """
      <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
        <name>Database Tools and SQL</name>
        <id>com.intellij.database</id>
        <product-descriptor code="PDB" release-date="__DATE__" release-version="__VERSION__"/>
        <description>
            <![CDATA[
              xxx for IntelliJ-based IDEs provides
            ]]>
        </description>
      </idea-plugin>
    """.trimIndent(),
    """
      <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
        <name>Database Tools and SQL</name>
        <id>com.intellij.database</id>
        <version>x-plugin-version</version>
        <idea-version since-build="new-since" until-build="new-until"/>
        <product-descriptor code="PDB" release-date="X-RELEASE-DATE-X" release-version="X-RELEASE-VERSION-X"/>
        <description>
            <![CDATA[
              xxx for IntelliJ-based IDEs provides
            ]]>
        </description>
      </idea-plugin>
    """.trimIndent(),
    toPublish = true,
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
        <idea-version since-build="new-since" until-build="new-until"/>
        <product-descriptor code="PCWMP" release-date="X-RELEASE-DATE-X" release-version="X-RELEASE-VERSION-X" optional="true"/>
      </idea-plugin>
    """.trimIndent(),
    retainProductDescriptorForBundledPlugin = true,
    toPublish = false,
  )

  private fun assertTransform(
    before: String,
    after: String,
    productName: String = "UnExistent",
    toPublish: Boolean = false,
    isEap: Boolean = false,
    retainProductDescriptorForBundledPlugin: Boolean = false,
  ) {
    val result = doPatchPluginXml(document = XMLParser.parse(before),
                                  pluginModuleName = "x-plugin-module-name",
                                  pluginVersion = "x-plugin-version",
                                  releaseDate = "X-RELEASE-DATE-X",
                                  releaseVersion = "X-RELEASE-VERSION-X",
                                  compatibleSinceUntil = Pair("new-since", "new-until"),
                                  toPublish = toPublish,
                                  retainProductDescriptorForBundledPlugin = retainProductDescriptorForBundledPlugin,
                                  isEap = isEap,
                                  productName = productName)
    assertThat(result).isEqualTo(after)
  }
}