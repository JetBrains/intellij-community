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



    public static void main(String[] args) {
        Cln s =  int[]::clone;
        IA a =  int[]::new;
        <error descr="Incompatible types. Found: '<method reference>', required: 'OnArrayTest.I'">I i = int[]::new;</error>
        <error descr="Incompatible types. Found: '<method reference>', required: 'OnArrayTest.Len<java.lang.String>'">Len<String> strLen = String[]::length;</error>
        ToStr<Integer> toStr = Integer[]::toString;
    }
}
