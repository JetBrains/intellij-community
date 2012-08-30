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
import java.util.*;
class Test1 {
  {
    Comparable<? extends Integer> c = (Integer o)->{
      return 0;
    };
    
    Comparable<? super Integer> c1 = (Integer o)->{
      return 0;
    };
  }
}


class Test2<U> {
  interface I<T> {
    void m(T t);
  }
  private void foo(I<? super U> i1) {}

  private void bar() {
    foo((U u) -> {});
    foo(u -> {});
  }

  interface I1<T> {}
  private <A extends I1<? super U>> A bar(A a) {
    foo((U u)->{});
    foo(u->{});
    return a;
  }
}

class TestNewExpression<E> {
    public TestNewExpression(Comparator<? super E> comparator) {
        //some code
    }
    public static void main(String[] args) {
      TestNewExpression<String> strs = new TestNewExpression<String>((o1, o2) -> {
            return o2.compareToIgnoreCase(o1);
        });
    }
}