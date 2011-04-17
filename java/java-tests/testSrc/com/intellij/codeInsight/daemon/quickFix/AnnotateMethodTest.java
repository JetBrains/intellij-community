package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInspection.AnnotateMethodFix;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.nullable.NullableStuffInspection;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NonNls;

public class AnnotateMethodTest extends LightQuickFix15TestCase {
  private boolean myMustBeAvailableAfterInvoke;

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/annotateMethod";
  }

  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new NullableStuffInspection(){
      @Override
      protected AnnotateMethodFix createAnnotateMethodFix(String defaultNotNull, String[] annotationsToRemove) {
        return new AnnotateMethodFix(defaultNotNull, annotationsToRemove){
          @Override
          public int annotateBaseMethod(final PsiMethod method, final PsiMethod superMethod, final Project project) {
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
}
