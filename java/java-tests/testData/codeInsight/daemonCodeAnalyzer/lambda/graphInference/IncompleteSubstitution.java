import java.util.*;
class Test {

    interface I<T> {
        T foo();
    }
    class Inner<T> { }


    <M1 extends List<?>> Inner<M1> staticFactory() {
      return null;
    }

    <M2 extends List<?>> void foo(I<M2> coll,
                                  Inner<M2> assertion)  { }

    void test(I<List<List<Integer>>> coll) {
        foo(coll, staticFactory());
    }
}

