import java.util.function.IntFunction;

public class LocalClass {
    void test(int x) {
        System.out.println(MyEnum.A);
    }

    private enum MyEnum {
        A, B, C;
    }
}
