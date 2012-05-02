import java.lang.Override;
import java.util.*;
class GenericsTest<T> {

    static <S> S next(GenericsTest<S> test)
    {
        System.out.println(test);
        return null;
    }

    public Iterator<T> iterator()
    {
        return new Iterator<T>() {
            @Override
            public boolean hasNext()
            {
                return false;
            }

            @Override
            public T next()
            {
                return GenericsTest.next(GenericsTest.this);
            }

            @Override
            public void remove()
            {
            }
        };
    }
}

class GenericsTest1<T> {

    static <S> S next1(GenericsTest1<S> test)
    {
        System.out.println(test);
        return null;
    }

    public Iterator<T> iterator()
    {
        return new Iterator<T>() {
            @Override
            public boolean hasNext()
            {
                return false;
            }

            @Override
            public T next()
            {
                return GenericsTest1.next1(GenericsTest1.this);
            }

            @Override
            public void remove()
            {
            }
        };
    }
}


class GenericsTest2<T> {

    static <S> S next2(GenericsTest2<S> test)
    {
        System.out.println(test);
        return null;
    }

    public Iterator<T> iterator()
    {
        return new Iterator<T>() {
            @Override
            public boolean hasNext()
            {
                return false;
            }

            @Override
            public T next()
            {
                return next2(GenericsTest2.this);
            }

            @Override
            public void remove()
            {
            }
        };
    }
}
class Example {
    private static <T> void assertThat(T actual, Matcher<? super T> matcher) {
      assert actual != null;
      assert matcher != null;
    }

    private static <E> Matcher<? super Collection<? extends E>> hasSize(int size) {
        assert size >=0;
        return new Matcher<Collection<? extends E>>() {
          @Override
          public void foo(Collection<? extends E> es) {
            System.out.println(es);
          }
        };
    }

    public static void main(String[] args) {
      List<Boolean> list = <warning descr="Unchecked assignment: 'java.util.ArrayList' to 'java.util.List<java.lang.Boolean>'">new ArrayList()</warning>;
      System.out.println(list);
      assertThat(new ArrayList<Boolean>(), hasSize(0));
    }

    private interface Matcher<T> {
      void foo(T t);
    }
}

abstract class IDEA57337<<warning descr="Type parameter 'S' is never used">S</warning>> {
    abstract <T> void foo(IDEA57337<? super IDEA57337<T>> x);
    void bar(IDEA57337<? super IDEA57337<?>> x){
        foo(x);
    }
}