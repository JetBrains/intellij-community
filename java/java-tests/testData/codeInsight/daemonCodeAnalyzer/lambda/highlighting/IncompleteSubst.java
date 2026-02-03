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

class LambdaTest<TT> {
  interface BinaryOperator<T> {

    public T eval(T left, T right);
  }

  interface Mapper<T, U> {
    U map(T t);
  }

  public static <T, U> LambdaTest<U> map(final Iterable<? extends T> iterable, final Mapper<? super T, ? extends U> mapper) {
    return null;
  }

  TT reduce(TT base, BinaryOperator<TT> reducer) {
    return null;
  }

  public void test() {
    final List<String> aStrings = Arrays.asList("1", "2", "3");
    map(aStrings, s -> s.length()).reduce(0, (l,  r) -> l + r);
  }
}

class LambdaTest2<TypeParam> {
  interface BinaryOperator<T> {
    public T eval(T left, T right);
  }

  interface Mapper<T, U> {
    U map(T t);
  }

  public <U> LambdaTest2<U> map(final Mapper<? super TypeParam, ? extends U> mapper) {
    return null;
  }

  TypeParam reduce(TypeParam base, BinaryOperator<TypeParam> reducer) {
    return null;
  }

  public void test() {
    final LambdaTest2<String> lt = new LambdaTest2<>();
    lt.map(s -> s.length()).reduce(0, (l,  r) -> l + r);
  }
}