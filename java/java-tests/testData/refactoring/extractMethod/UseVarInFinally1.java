import java.util.concurrent.ThreadLocalRandom;

public class A {
    void f() {
        String s = "";
        try {
            s = "a";
            <selection>if (r()) throw new RuntimeException();
            s = "b";</selection>
        } finally {
            System.out.println(s);
        }
    }

    private boolean r() {
        return ThreadLocalRandom.current().nextBoolean();
    }
}