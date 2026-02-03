import java.io.InputStream;

class Test {
    InputStream test(boolean condition){
        final InputStream stream = System.in;
        <selection>int x = 42;
        if (condition) return stream;
        if (!condition) return stream;</selection>
        System.out.println(x);
        return null;
    }
}