import java.util.function.IntFunction;

public class LocalClass {
    void test(int x) {
        System.out.println(new MyRecord(1, 2));
    }

    private record MyRecord(int a, int b) {
        void test() {
            System.out.println(x); // compilation error
        }
    }
}
