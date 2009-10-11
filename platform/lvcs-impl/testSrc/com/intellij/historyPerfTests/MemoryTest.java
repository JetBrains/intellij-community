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

package com.intellij.historyPerfTests;

import com.intellij.history.utils.RunnableAdapter;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class MemoryTest extends LocalVcsPerformanceTestCase {
  @Test
  public void testMemoryAfterFirstBuild() {
    assertMemoryUsage(44, new RunnableAdapter() {
      public void doRun() throws Exception {
        buildVcsTree();
      }
    });
  }

  @Test
  public void testMemoryAfterLoad() {
    buildVcsTree();

    vcs.save();
    closeStorage();
    vcs = null;

    assertMemoryUsage(43, new RunnableAdapter() {
      public void doRun() throws Exception {
        initVcs();
      }
    });
  }
}