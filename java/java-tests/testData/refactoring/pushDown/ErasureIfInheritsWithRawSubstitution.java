import java.util.*;

class A<T> {
  <S extends T, K extends List<List<T>>> <caret>foo(List<? extends T> l1, List<? extends S> l2, List<? extends K> l3, S s, K k, T t) {
    Collections.<T>emptyList();
  }
}

class B extends A {}