import java.util.concurrent.atomic.AtomicReferenceArray;

class Test {
    AtomicReferenceArray<String> s = new AtomicReferenceArray<String>(new String[2]);

    void foo() {
        s.set(0, "");
        System.out.println(s.get(0));

    }
}