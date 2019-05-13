import java.util.Comparator;
import java.util.List;

class Test {
  public static final Comparator<Object> ORDER_AWARE_COMPARATOR = null;

  public static void orderAwareSort(List<?> data) {
    sort(data, ORDER_AWARE_COMPARATOR);
  }

  public static <T> void sort(List<T> list, Comparator<? super T> c) {}
}

class FooBar<T> {
  void foo(final FooBar<?> fooBar){
    fooBar.supertype<error descr="'supertype(java.lang.Class<? super capture<?>>)' in 'FooBar' cannot be applied to '(java.lang.Class<java.lang.Iterable>)'">(Iterable.class)</error>;
  }

  void foo1(final FooBar<? super T> fooBar){
    fooBar.supertype<error descr="'supertype(java.lang.Class<? super capture<? super T>>)' in 'FooBar' cannot be applied to '(java.lang.Class<java.lang.Iterable>)'">(Iterable.class)</error>;
  }

  void foo2(final FooBar<? extends T> fooBar){
    fooBar.supertype<error descr="'supertype(java.lang.Class<? super capture<? extends T>>)' in 'FooBar' cannot be applied to '(java.lang.Class<java.lang.Iterable>)'">(Iterable.class)</error>;
  }

  void supertype(Class<? super T> superclass) {}
}

