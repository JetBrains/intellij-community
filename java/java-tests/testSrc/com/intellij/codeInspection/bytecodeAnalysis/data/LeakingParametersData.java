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

import com.intellij.codeInspection.bytecodeAnalysis.ExpectLeaking;

/**
 * @author lambdamix
 */
public class LeakingParametersData {
  int z;

  void test01(@ExpectLeaking Object o1, @ExpectLeaking Object o2, @ExpectLeaking Object o3) {
    o1.toString();
    o2.toString();
    o3.toString();
  }

  void test02(@ExpectLeaking LeakingParametersData d) {
    System.out.println(d.z);
  }

  void test03(int i, @ExpectLeaking LeakingParametersData d) {
    System.out.println(d.z);
  }

  void test04(long i, @ExpectLeaking LeakingParametersData d) {
    System.out.println(d.z);
  }
}
