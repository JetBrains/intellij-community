import java.util.concurrent.atomic.AtomicReference;

class Test {
    AtomicReference<String> s = new AtomicReference<String>("");

    void foo() {
        if (s.get() == null) {
           System.out.println(s.get());
        }
    }
}