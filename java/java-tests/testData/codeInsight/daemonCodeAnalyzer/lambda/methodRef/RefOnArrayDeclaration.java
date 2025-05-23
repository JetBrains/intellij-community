class OnArrayTest {
    interface Cln {
       Object m(int[] i);
    }

    interface IA {
        void m(int i);
    }

    interface I {
        void m(int[] i);
    }

    interface Len<T> {
        int len(T[] ta);
    }

    interface ToStr<T> {
        String m(T[] ta);
    }

    interface ArrayReturnType<T> {
        T make(int size);
    }

    static class Foo<X> { }
    interface ObjectArrayReturnType {
        Object make(int size);
    }

    public static void main(String[] args) {
        Cln s =  int[]::clone;
        IA a =  int[]::new;
        I i = int[]::<error descr="Incompatible types. Found: '<method reference>', required: 'OnArrayTest.I'">new</error>;
        Len<String> strLen = String[]::<error descr="Cannot resolve method 'length'">length</error>;
        ToStr<Integer> toStr = Integer[]::toString;

        ArrayReturnType<String[]> a1 = String[]::new;
        ArrayReturnType<String[][]> a2 = String[][]::new;
        ArrayReturnType<String[]> a3 = <error descr="Bad return type in method reference: cannot convert int[] to java.lang.String[]">int[]::new</error>;

        ObjectArrayReturnType a4 = Foo<?>[]::new;
        ObjectArrayReturnType a5 = Foo<error descr="Generic array creation not allowed"><? extends String></error>[]::new;
    }
}

class IDEA106973 {
  interface Function<T, R> {
    R apply(T t);
  }

  {
    Function<Integer, String[]> a  = String[] :: new;
    Function<String, String[]> a1  = String[] :: <error descr="Incompatible types. Found: '<method reference>', required: 'IDEA106973.Function<java.lang.String,java.lang.String[]>'">new</error>;
    Function<Short, String[]> a2  = String[] :: new;
  }
}

class IDEA268866 {
  interface Function<A, B> {
    B apply(A a);
  }
  private static <T, K, U> void m(Function<? super T, ? extends K> keyMapper,
                                  Function<? super T, ? extends U> valueMapper) {
  }

  private static void main(Function<Integer, Integer> identity) {
    m(identity, int[]::new);
  }
}