import java.util.function.IntFunction;

public class LocalClass {
    void test(int x) {
        record <caret>R(int a, int b) {
            void test() {
                System.out.println(x); // compilation error
            }
        }
        System.out.println(new R(1, 2));
    }
}
