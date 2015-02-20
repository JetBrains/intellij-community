/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.run

import com.intellij.application.options.PathMacrosCollector
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class JavaPathMacroCollectorTest extends LightCodeInsightFixtureTestCase {
  public void testJunitConfiguration() {
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
      <envs />
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
    def element = JDOMUtil.loadDocument(text).rootElement
    assert PathMacrosCollector.getMacroNames(element).empty
  }

}
