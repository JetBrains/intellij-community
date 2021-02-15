import java.util.Arrays;

class Test {
    void test(int x, int y, int z) {
        while (x > 0) {
            boolean temp = z > 0;
            if (!(y < 0 || temp)) break;
            z++;
        }
    }
}