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
        ObjectArrayReturnType a5 = Foo<? extends String>[]::new;
    }
}
