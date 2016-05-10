import java.util.*;

class A<T> {
}

class B extends A {
    <S extends Object, K extends List> B(List l1, List<? extends S> l2, List<? extends K> l3, S s, K k, Object t) {
      Collections.emptyList();
    }
}