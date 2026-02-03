// "Use 'fill' method without lambda" "true-preview"
import java.util.Arrays;

class Test {
    public void test(int[] arr) {
        Arrays.setAll(arr, x <caret>-> 0);
    }
}