// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java.inliner;

import com.intellij.codeInspection.dataFlow.java.CFGBuilder;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NotNull;

public class TransformInliner implements CallInliner {
  private static final CallMatcher TRANSFORM_METHODS = CallMatcher.anyOf(
    CallMatcher.instanceCall("reactor.core.publisher.Mono", "as").parameterCount(1),
    CallMatcher.instanceCall("reactor.core.publisher.Flux", "as").parameterCount(1),
    CallMatcher.instanceCall("reactor.core.publisher.ParallelFlux", "as").parameterCount(1),
    CallMatcher.instanceCall("io.reactivex.Completable", "as").parameterCount(1),
    CallMatcher.instanceCall("io.reactivex.Flowable", "as").parameterCount(1),
    CallMatcher.instanceCall("io.reactivex.Maybe", "as").parameterCount(1),
    CallMatcher.instanceCall("io.reactivex.Observable", "as").parameterCount(1),
    CallMatcher.instanceCall("io.reactivex.Single", "as").parameterCount(1),
    CallMatcher.instanceCall("io.reactivex.rxjava3.core.Completable", "to").parameterCount(1),
    CallMatcher.instanceCall("io.reactivex.rxjava3.core.Flowable", "to").parameterCount(1),
    CallMatcher.instanceCall("io.reactivex.rxjava3.core.Maybe", "to").parameterCount(1),
    CallMatcher.instanceCall("io.reactivex.rxjava3.core.Observable", "to").parameterCount(1),
    CallMatcher.instanceCall("io.reactivex.rxjava3.core.Single", "to").parameterCount(1),
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_STRING, "transform").parameterCount(1),
    CallMatcher.instanceCall("one.util.streamex.BaseStreamEx", "chain").parameterCount(1)
  );

  @Override
  public boolean tryInlineCall(@NotNull CFGBuilder builder, @NotNull PsiMethodCallExpression call) {
    if (TRANSFORM_METHODS.test(call)) {
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if (qualifier != null) {
        PsiExpression fn = call.getArgumentList().getExpressions()[0];
        builder.pushExpression(qualifier)
          .evaluateFunction(fn)
          .invokeFunction(1, fn)
          .resultOf(call);
        return true;
      }
    }
    return false;
  }
}
