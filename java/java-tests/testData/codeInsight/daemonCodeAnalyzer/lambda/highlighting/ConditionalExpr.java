/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
class Test {
  public interface I {
    int m();
  }

  public interface I1 {
    int m(int y);
  }

  {
    boolean flag = true;
    I i =  flag ? (() -> 123)   : (() -> 222);
    I i1 =  flag ? (() -> {<error descr="Missing return statement">}</error>)   : (() -> 222);
    Object i2 =  flag ? (<error descr="Target type of a lambda conversion must be an interface">() -> 42</error>)   : (<error descr="Target type of a lambda conversion must be an interface">() -> 222</error>);
    I i3 =  flag ? (<error descr="Incompatible parameter types in lambda expression: wrong number of parameters: expected 0 but found 1">(x)</error> -> 42)   : (() -> 222);
    I i4 =  flag ? (() -> 42) : new I() {
      @Override
      public int m() {
        return 0;
      }
    };
  }
}

class Test1 {
  interface I<T, V> {
    V _(T t);
  }

  static <V> void bar(I<String, V> ii, I<V, String> ik){}

  {
    bar(s -> s.equals("") ? 0 : 1, i -> "");
  }
}

