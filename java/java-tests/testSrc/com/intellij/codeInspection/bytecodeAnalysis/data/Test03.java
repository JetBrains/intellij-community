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
package com.intellij.codeInspection.bytecodeAnalysis.data;

import com.intellij.codeInspection.bytecodeAnalysis.BytecodeAnalysisConverter;
import com.intellij.codeInspection.bytecodeAnalysis.ExpectNoPsiKey;

/**
 * @author lambdamix
 */
public class Test03<A> {

  public static class Inner1 {
    public Inner1(Object o) {

    }
    public Inner1[] Inner1test01(Inner1[] tests, Inner1... ellipsis) {
      return tests;
    }
  }

  public class Inner2 {
    public Inner2(A o) {

    }
    public A[] Inner2test01(A[] tests, A... ellipsis) {
      return tests;
    }
  }

  public Test03(@TestAnnotation int x) {}

  @ExpectNoPsiKey
  public BytecodeAnalysisConverter.InternalKey test01(BytecodeAnalysisConverter.InternalKey converter) {
    return converter;
  }

  public Test03[] test02(Test03[] tests) {
    return tests;
  }
}
