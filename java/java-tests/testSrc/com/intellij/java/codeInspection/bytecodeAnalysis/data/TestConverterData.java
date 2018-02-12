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
package com.intellij.java.codeInspection.bytecodeAnalysis.data;

import com.intellij.codeInspection.bytecodeAnalysis.BytecodeAnalysisConverter;
import com.intellij.java.codeInspection.bytecodeAnalysis.ExpectNoPsiKey;

/**
 * @author lambdamix
 */
public class TestConverterData {

  public static class StaticNestedClass {
    public StaticNestedClass(Object o) {

    }
    public StaticNestedClass[] test01(StaticNestedClass[] ns, StaticNestedClass... ellipsis) {
      return ns;
    }
  }

  public class InnerClass {
    // a reference to outer class should be inserted when translating PSI -> ASM
    public InnerClass(Object o) {}

    public InnerClass[] Inner2test01(InnerClass[] tests, InnerClass... ellipsis) {
      return tests;
    }
  }

  public static class GenericStaticNestedClass<A> {
    public GenericStaticNestedClass(A a) {

    }
    public GenericStaticNestedClass[] test01(GenericStaticNestedClass[] ns, GenericStaticNestedClass... ellipsis) {
      return ns;
    }

    public GenericStaticNestedClass<A>[] test02(GenericStaticNestedClass<A>[] ns, GenericStaticNestedClass<A>... ellipsis) {
      return ns;
    }

    public class GenericInnerClass<B> {
      public GenericInnerClass(B b) {}

      public <C> GenericStaticNestedClass<A> test01(GenericInnerClass<C> c) {
        return GenericStaticNestedClass.this;
      }
    }
  }

  public TestConverterData(int x) {}

  // BytecodeAnalysisConverter class is not in the project path, so translation from PSI is impossible
  @ExpectNoPsiKey
  public BytecodeAnalysisConverter test01(BytecodeAnalysisConverter converter) {
    return converter;
  }

  @TestAnnotation
  public TestConverterData[] test02(@TestAnnotation TestConverterData[] tests) {
    return tests;
  }

  public boolean[] test03(boolean[] b) {
    return b;
  }
}
