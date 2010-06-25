import java.io.FileNotFoundException;
import java.util.*;

interface PrivilegedExceptionAction <E extends Exception> {
    void run() throws E;
}

class AccessController {
    public static <E extends Exception> Object doPrivileged(PrivilegedExceptionAction<E> action) throws E {
        return null;
    }
}
class Test {
    public static void main(String[] args) {
        try {
            AccessController.doPrivileged(
                    new PrivilegedExceptionAction<FileNotFoundException>() {
                        public void run() throws FileNotFoundException {
                        }
                    });
        } catch (FileNotFoundException f) {
        }
    }

// @#@! mock JDK Class does not take params
//    static <T> T create(Class<T> t) throws InstantiationException, IllegalAccessException {
//        return t.newInstance();
//    }
}

//IDEADEV-6390
class Printer<T> {
    private final List<T> _elements;

    private Printer(final Collection<? extends T> col) {
        _elements = new ArrayList<T>(col);
    }

    public static <T> Printer<T> build(final Collection<? extends T> col) {
        return new Printer<T>(col);
    }

    public static <T, S extends T> Printer<T> build(final S... elements) {
        return new Printer<T>(Arrays.asList(elements));
    }

    public void print() {
        for (final T element : _elements) {
            System.out.println(element);
        }

    }

    public static void main(final String[] args) {
        final Printer<?> objects =  build(Integer.valueOf(5), Boolean.TRUE, "A String!"); //this is OK
         objects.print();
    }

}
//end of IDEADEV-6390

//IDEADEV-6738
interface I1<P1 extends I1<P1,P2>, P2 extends I2<P1,P2>>{}
interface I2<P1 extends I1<P1,P2>, P2 extends I2<P1,P2>>{}

class C1 implements I1<C1,C2>{}
class C2 implements I2<C1,C2>{}

class U {
    public static <P1 extends I1<P1,P2>, P2 extends I2<P1,P2>> P1 test(P1 p1) {
        return null;
    }
    {
        C1 c = new C1();
        U.test(c); //this should be OK
    }
}
//end of IDEADEV-6738

///////////////////////////////////
public class Err {
    void f() {
        Decl[] extensions = getExtensions(Decl.EXTENSION_POINT_NAME);
    }

    static <T> T[] getExtensions(List<T> tExtensionPointName) {
        return null;
    }

    public static class Decl<K,V> {
        public static List<Decl> EXTENSION_POINT_NAME = null;
    }

}
/////////////////////////////////////