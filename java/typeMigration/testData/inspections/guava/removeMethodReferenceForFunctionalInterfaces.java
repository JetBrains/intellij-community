/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.google.common.base.Function;

import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
  int m1() {
    ArrayList<String> strings = new ArrayList<>();
    Stream<String> it = strings.stream();

    Function<String, String> functi<caret>on = new Function<String, String>() {
      @Override
      public String apply(String s) {
        return s + s;
      }
    };
    return it.map(function::apply).collect(Collectors.toList()).size();
  }
}