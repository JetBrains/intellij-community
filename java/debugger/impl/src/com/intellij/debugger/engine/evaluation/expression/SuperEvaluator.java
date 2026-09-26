// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation.expression;

/**
 * @author Eugene Zhuravlev
 */
public class SuperEvaluator extends ThisEvaluator {

  public SuperEvaluator(CaptureTraverser traverser) {
    super(traverser);
  }
}
