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
        String _(T[] ta);
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
        <error descr="Incompatible types. Found: '<method reference>', required: 'OnArrayTest.I'">I i = int[]::new;</error>
        <error descr="Incompatible types. Found: '<method reference>', required: 'OnArrayTest.Len<java.lang.String>'">Len<String> strLen = String[]::length;</error>
        ToStr<Integer> toStr = Integer[]::toString;

        ArrayReturnType<String[]> a1 = String[]::new;
        ArrayReturnType<String[][]> a2 = String[][]::new;
        <error descr="Incompatible types. Found: '<method reference>', required: 'OnArrayTest.ArrayReturnType<java.lang.String[]>'">ArrayReturnType<String[]> a3 = int[]::new;</error>
        
        ObjectArrayReturnType a4 = Foo<?>[]::new;
        ObjectArrayReturnType a5 = <error descr="Generic array creation">Foo<? extends String>[]</error>::new;
    }
}


class IDEA106973 {
  interface Function<T, R> {
    R apply(T t);
  }
  
  {
    Function<Integer, String[]> a  = String[] :: new;
    <error descr="Incompatible types. Found: '<method reference>', required: 'IDEA106973.Function<java.lang.String,java.lang.String[]>'">Function<String, String[]> a1  = String[] :: new;</error>
    Function<Short, String[]> a2  = String[] :: new;
  }
}