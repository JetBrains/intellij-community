/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.run

import com.intellij.application.options.PathMacrosCollector
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.JdomKt

class JavaPathMacroCollectorTest extends LightCodeInsightFixtureTestCase {
  void testJunitConfiguration() {
    String text = '''
  <component name="RunManager" selected="JUnit.FooWithComments.test$withDollar$2">
    <configuration default="false" name="FooWithComments.test$withDollar$2" type="JUnit" factoryName="JUnit" temporary="true" nameIsGenerated="true">
      <module name="idea.folding.problem" />
      <option name="ALTERNATIVE_JRE_PATH_ENABLED" value="false" />
      <option name="ALTERNATIVE_JRE_PATH" />
      <option name="PACKAGE_NAME" value="idea.folding.problem" />
      <option name="MAIN_CLASS_NAME" value="idea.folding.problem.FooWithComments" />
      <option name="METHOD_NAME" value="test$withDollar$2" />
      <option name="TEST_OBJECT" value="method" />
      <option name="VM_PARAMETERS" />
      <option name="PARAMETERS" />
      <option name="WORKING_DIRECTORY" value="$PROJECT_DIR$" />
      <option name="ENV_VARIABLES" />
      <option name="PASS_PARENT_ENVS" value="true" />
      <option name="TEST_SEARCH_SCOPE">
        <value defaultName="moduleWithDependencies" />
      </option>
      <patterns />
      <method />
    </configuration>
    <list size="4">
      <item index="0" class="java.lang.String" itemvalue="Application.Foo" />
      <item index="1" class="java.lang.String" itemvalue="Application.FooWithComments" />
      <item index="2" class="java.lang.String" itemvalue="Application.A" />
      <item index="3" class="java.lang.String" itemvalue="JUnit.FooWithComments.test$withDollar$2" />
    </list>
    <recent_temporary>
      <list size="4">
        <item index="0" class="java.lang.String" itemvalue="JUnit.FooWithComments.test$withDollar$2" />
      </list>
    </recent_temporary>
  </component>
'''
    assert PathMacrosCollector.getMacroNames(JdomKt.loadElement(text)).empty
  }

}
