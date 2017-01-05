import java.util.concurrent.ThreadLocalRandom;

public class A {
    void f() {
        String s = "";
        try {
            <selection>s = "a";
            if (r()) throw new RuntimeException();</selection>
            s = "b";
        } finally {
            System.out.println(s);
        }
    }

    private boolean r() {
        return ThreadLocalRandom.current().nextBoolean();
    }
}