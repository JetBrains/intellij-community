/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl;

import com.intellij.testFramework.FileEditorManagerTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jdom.JDOMException;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * @author Dmitry Avdeev
 */
public class JavaFileEditorManagerTest extends FileEditorManagerTestCase {

  public void testAsyncOpening() throws JDOMException, ExecutionException, InterruptedException, IOException {
    openFiles("<component name=\"FileEditorManager\">\n" +
              "    <leaf>\n" +
              "      <file leaf-file-name=\"Bar.java\" pinned=\"false\" current=\"true\" current-in-tab=\"true\">\n" +
              "        <entry file=\"file://$PROJECT_DIR$/src/Bar.java\">\n" +
              "          <provider selected=\"true\" editor-type-id=\"text-editor\">\n" +
              "            <state vertical-scroll-proportion=\"0.032882012\" vertical-offset=\"0\" max-vertical-offset=\"517\">\n" +
              "              <caret line=\"1\" column=\"26\" selection-start=\"45\" selection-end=\"45\" />\n" +
              "              <folding>\n" +
              "                <element signature=\"e#69#70#0\" expanded=\"true\" />\n" +
              "              </folding>\n" +
              "            </state>\n" +
              "          </provider>\n" +
              "        </entry>\n" +
              "      </file>\n" +
              "    </leaf>\n" +
              "  </component>");
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/java/java-tests/testData/fileEditorManager";
  }
}
