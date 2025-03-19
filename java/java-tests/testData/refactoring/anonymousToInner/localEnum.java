import java.util.function.IntFunction;

public class LocalClass {
    void test(int x) {
        enum The<caret>Enum {
            A, B, C;
        }
        System.out.println(TheEnum.A);
    }
}
