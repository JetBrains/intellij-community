// "Use 'fill' method without lambda" "false"
import java.util.Arrays;

class Test {
    public void test(int[] arr) {
        Arrays.setAll(arr, x <caret>-> x);
    }
}