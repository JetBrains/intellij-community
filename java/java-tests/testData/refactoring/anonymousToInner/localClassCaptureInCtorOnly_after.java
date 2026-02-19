import java.util.function.IntFunction;

public class LocalClass {
    void test(int x, int y) {

        new InnerClass(x, y);
        new InnerClass(new int[]{1, 2, 3}, x, y);
    }

    private static class InnerClass {
        InnerClass(int x, int y) {
            System.out.println(x);
        }
        
        InnerClass(int[] data, int x, int y) {
            System.out.println(y);
        }
    }
}
