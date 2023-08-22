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
import java.util.Comparator;

class ComparatorIsNotReflexive implements Comparator<Integer> {
  public int compare(Integer v1, Integer v2) {
    if(v1 > v2) return 1;
    return <warning descr="Comparator does not return 0 for equal elements">-1</warning>;
  }

  Comparator<String> lambda = (a, b) -> a.length() > b.length() ? 1 : <warning descr="Comparator does not return 0 for equal elements">-1</warning>;

  Comparator<String> lambda2 = (a, b) -> a.length() > b.length() ? 1 :
                                         (a.length() < b.length() ? 0 : <warning descr="Comparator does not return 0 for equal elements">-1</warning>);

  Comparator<String> lambda3 = (a, b) -> (a.length() > b.length() ? 0 :
                                         <warning descr="Comparator does not return 0 for equal elements">Math.random() > 0.5 ? (-1) : (1)</warning>);

  Comparator<byte[]> arrayComparator = <warning descr="Comparator never returns positive values">(b1, b2)</warning> -> {
    if(b1.length != b2.length) return 0; // typo: == was intended
    return b1.length > b2.length ? 1 : <warning descr="Comparator does not return 0 for equal elements">-1</warning>;
  };

  Comparator<String> cmp = (a,b) -> test();

  static int test() {
    throw new RuntimeException();
  }
}
