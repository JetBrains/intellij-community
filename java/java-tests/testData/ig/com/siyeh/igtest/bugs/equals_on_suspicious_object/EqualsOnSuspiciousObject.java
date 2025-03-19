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
import java.util.Objects;

public class EqualsOnSuspiciousObject {
  public void test(StringBuilder sb1, StringBuilder sb2) {
    if(sb1.<warning descr="Suspicious call to 'equals()' on 'StringBuilder' object">equals</warning>(sb2)) {
      System.out.println("Strange");
    }
    if("xyz".<warning descr="Suspicious call to 'equals()' on 'StringBuilder' object">equals</warning>(sb1)) {
      System.out.println("Strange");
    }
    if(Objects.<warning descr="Suspicious call to 'equals()' on 'StringBuilder' object">equals</warning>(sb1, sb2)) {
      System.out.println("Strange");
    }
    if(java.util.function.Predicate.<warning descr="Suspicious call to 'equals()' on 'StringBuilder' object">isEqual</warning>(sb1).test(sb2)) {
      System.out.println("Strange");
    }
  }
}