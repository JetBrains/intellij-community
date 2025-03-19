// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore.xml

import com.intellij.configurationStore.JbXmlOutputter
import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rethrowLoggedErrorsIn
import com.intellij.util.SystemProperties
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Tag
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.StringWriter

internal class ForbidSensitiveInformationTest {
  @Rule
  @JvmField
  val disposableRule = DisposableRule()

  @Before
  fun before() {
    DefaultLogger.disableStderrDumping(disposableRule.disposable)
  }

  @Test
  fun `do not store password as attribute`(): Unit = rethrowLoggedErrorsIn {
    @Tag("bean")
    class Bean {
      @Attribute
      var password: String? = null

      @Attribute
      var foo: String? = null
    }

    val bean = Bean()
    bean.foo = "module"
    bean.password = "ab"
    // it is not part of XML bindings to ensure that even if you will use JDOM directly, you cannot output sensitive data
    // so, testSerializer must not throw error
    val element = assertSerializer(bean, "<bean password=\"ab\" foo=\"module\" />")

    assertThatThrownBy {
      val xmlWriter = JbXmlOutputter()
      xmlWriter.output(element, StringWriter())
    }.hasMessage("Attribute bean.@password probably contains sensitive information")

    // No exception if 'password' has no value
    bean.password = null
    val element2 = assertSerializer(bean, "<bean foo=\"module\" />")
    val xmlWriter = JbXmlOutputter()
    xmlWriter.output(element2, StringWriter())
  }

  @Test
  fun `do not store password as element`(): Unit = rethrowLoggedErrorsIn {
    @Tag("component")
    class Bean {
      var password: String? = null

      @Attribute
      var name: String? = null
    }

    val bean = Bean()
    bean.name = "someComponent"
    bean.password = "ab"
    // it is not part of XML bindings to ensure that even if you will use JDOM directly, you cannot output sensitive data
    // so, testSerializer must not throw error
    val element = assertSerializer(bean, """
        <component name="someComponent">
          <option name="password" value="ab" />
        </component>
      """.trimIndent())

    assertThatThrownBy {
      val xmlWriter = JbXmlOutputter(
        storageFilePathForDebugPurposes = "${FileUtilRt.toSystemIndependentName(SystemProperties.getUserHome())}/foo/bar.xml")
      xmlWriter.output(element, StringWriter())
    }.hasMessage("""Element component@someComponent.option.@name=password probably contains sensitive information (file: ~/foo/bar.xml)""")

    // No exception if 'password' has no value
    bean.password = null
    val element2 = assertSerializer(bean, """
        <component name="someComponent">
          <option name="password" />
        </component>
      """.trimIndent())
    val xmlWriter = JbXmlOutputter()
    xmlWriter.output(element2, StringWriter())
  }

  @Test
  fun `do not store password as tag`(): Unit = rethrowLoggedErrorsIn {
    @Tag("server")
    class Server {
      @Tag("password")
      var password: String? = null

      @Attribute
      var name: String? = null
    }

    val server = Server()
    server.name = "youtrack"
    server.password = "ab"
    // it is not part of XML bindings to ensure that even if you will use JDOM directly, you cannot output sensitive data
    // so, testSerializer must not throw error
    val element = assertSerializer(server, """
        <server name="youtrack">
          <password>ab</password>
        </server>
      """.trimIndent())

    assertThatThrownBy {
      val xmlWriter = JbXmlOutputter(
        storageFilePathForDebugPurposes = "${FileUtilRt.toSystemIndependentName(SystemProperties.getUserHome())}/foo/bar.xml")
      xmlWriter.output(element, StringWriter())
    }.hasMessage("""Element server.password probably contains sensitive information (file: ~/foo/bar.xml)""")

    // No exception if 'password' has no value
    server.password = null
    val element2 = assertSerializer(server, """
        <server name="youtrack">
          <password />
        </server>
      """.trimIndent())
    val xmlWriter = JbXmlOutputter()
    xmlWriter.output(element2, StringWriter())
  }

  @Test
  fun `configuration name with password word`() {
    @Tag("bean")
    class Bean {
      @OptionTag(tag = "configuration", valueAttribute = "bar")
      var password: String? = null

      // check that use or save password fields are ignored
      var usePassword = false
      var savePassword = false
      var rememberPassword = false
      @Attribute("keep-password")
      var keepPassword = false
    }

    val bean = Bean()
    bean.password = "ab"
    bean.usePassword = true
    bean.keepPassword = true
    bean.rememberPassword = true
    bean.savePassword = true
    // it is not part of XML bindings to ensure that even if you will use JDOM directly, you cannot output sensitive data
    // so, testSerializer must not throw error
    val element = assertSerializer(bean, """
      <bean keep-password="true">
        <option name="rememberPassword" value="true" />
        <option name="savePassword" value="true" />
        <option name="usePassword" value="true" />
        <configuration name="password" bar="ab" />
      </bean>
      """.trimIndent())

    val xmlWriter = JbXmlOutputter()
    val stringWriter = StringWriter()
    xmlWriter.output(element, stringWriter)
    assertThat(stringWriter.toString()).isEqualTo("""
        <bean keep-password="true">
          <option name="rememberPassword" value="true" />
          <option name="savePassword" value="true" />
          <option name="usePassword" value="true" />
          <configuration name="password" bar="ab" />
        </bean>
      """.trimIndent())
  }
}
