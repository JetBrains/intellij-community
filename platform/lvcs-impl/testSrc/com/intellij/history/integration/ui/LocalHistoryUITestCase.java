/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.history.integration.ui;

import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.history.integration.IntegrationTestCase;
import com.intellij.testFramework.SkipInHeadlessEnvironment;

@SkipInHeadlessEnvironment
public abstract class LocalHistoryUITestCase extends IntegrationTestCase {
  protected void assertContent(String expected, DiffContent actual) {
    actual.onAssigned(true);
    try {
      assertEquals(expected, ((DocumentContent)actual).getDocument().getText());
    } finally {
      actual.onAssigned(false);
    }
  }
}
