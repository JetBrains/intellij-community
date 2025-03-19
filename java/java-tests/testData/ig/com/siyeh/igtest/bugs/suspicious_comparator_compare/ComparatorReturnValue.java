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

class ComparatorReturnValue implements Comparator<Integer> {
  public int <warning descr="Comparator never returns negative values">compare</warning>(Integer v1, Integer v2) {
    if(v1 > v2) return 1;
    if(v1 < v2) return 2;
    return 0;
  }

  Comparator<String> cmp = <warning descr="Comparator never returns positive values">(s1, s2)</warning> -> s1.equals(s2) ? 0 : s1.charAt(0) < s2.charAt(0) ? -1 : -2;
}
