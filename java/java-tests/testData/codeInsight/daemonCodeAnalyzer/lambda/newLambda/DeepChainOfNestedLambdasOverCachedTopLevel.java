
/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.IntStream.range;

class Test {

  public static void main(String[] args) {
    final int[] ints = new int[9];
    final Stream<Object> empty = Stream.empty();
    final IntStream range = range(0, 9);
    empty.flatMap(e -> range.mapToObj(i -> range.mapToObj(j -> ints)));
    final Stream<int[]> stream = empty.flatMap(e -> range(9, 0).mapToObj(i -> range.mapToObj(j -> ints))).flatMap(s -> s);
    System.out.println(stream);
  }
}
