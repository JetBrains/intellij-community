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
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.codeInspection.AnnotateMethodFix;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.nullable.NullableStuffInspection;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AnnotateMethodTest extends LightQuickFixTestCase {
  private boolean myMustBeAvailableAfterInvoke;

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/annotateMethod";
  }

  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new NullableStuffInspection(){
      @Override
      protected AnnotateMethodFix createAnnotateMethodFix(String defaultNotNull, String[] annotationsToRemove) {
        return new AnnotateMethodFix(defaultNotNull, annotationsToRemove){
          @Override
          public int shouldAnnotateBaseMethod(final PsiMethod method, final PsiMethod superMethod, final Project project) {
            @NonNls String name = method.getName();
            int ret = name.startsWith("annotateBase") ? 0  // yes, annotate all
                      : name.startsWith("dontAnnotateBase") ? 1 // do not annotate base
                        : 2; //abort
            myMustBeAvailableAfterInvoke = ret == 2;
            return ret;
          }
        };
      }

    }};
  }

  @Override
  protected boolean shouldBeAvailableAfterExecution() {
    return myMustBeAvailableAfterInvoke;
  }

  public void test() throws Exception { doAllTests(); }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_5;
  }
}
abstract class P2 {
    @NotNull<caret>
    String foo(@NotNull String p) {
        return p;
    }

    Object o = new P2() {
        @Override
        String foo(String p) {
            return "";
        }
    };
}