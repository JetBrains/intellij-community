/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class InspectionLIfeCycleTest extends LightDaemonAnalyzerTestCase {
  public void testInspectionFinishedCalledOnce() {
    String text = "class LQF {\n" +
                  "    int f;\n" +
                  "    public void me() {\n" +
                  "        <caret>\n" +
                  "    }\n" +
                  "}";
    configureFromFileText("x.java", text);

    final AtomicInteger startedCount = new AtomicInteger();
    final AtomicInteger finishedCount = new AtomicInteger();
    final Key<Object> KEY = Key.create("just key");

    LocalInspectionTool tool = new LocalInspectionTool() {
      @Nls
      @NotNull
      @Override
      public String getGroupDisplayName() {
        return "fegna";
      }

      @Nls
      @NotNull
      @Override
      public String getDisplayName() {
        return getGroupDisplayName();
      }

      @NotNull
      @Override
      public String getShortName() {
        return getGroupDisplayName();
      }

      @NotNull
      @Override
      public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
        };
      }

      @Override
      public void inspectionStarted(@NotNull LocalInspectionToolSession session, boolean isOnTheFly) {
        startedCount.incrementAndGet();
        session.putUserData(KEY, session);
      }

      @Override
      public void inspectionFinished(@NotNull LocalInspectionToolSession session, @NotNull ProblemsHolder problemsHolder) {
        finishedCount.incrementAndGet();
        assertEmpty(problemsHolder.getResults());
        assertSame(session, session.getUserData(KEY));
      }
    };
    enableInspectionTool(tool);

    List<HighlightInfo> infos = highlightErrors();
    assertEmpty(infos);

    assertEquals(1, startedCount.get());
    assertEquals(1, finishedCount.get());
  }
}
