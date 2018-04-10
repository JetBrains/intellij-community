// "Use 'fill' method without lambda" "false"
import java.util.Arrays;

class Test {
    public void test(String[] arr, Object[] arr2) {
        Arrays.setAll(String, x <caret>-> (String)(arr2[x]));
    }
}