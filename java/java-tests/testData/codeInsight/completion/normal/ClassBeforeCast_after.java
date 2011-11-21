import java.io.FileInputStream;

public class Foo {
    public static void fpp(Object o) {
        FileInputStream<caret>
        ((Object) o).notify();
    }
}
