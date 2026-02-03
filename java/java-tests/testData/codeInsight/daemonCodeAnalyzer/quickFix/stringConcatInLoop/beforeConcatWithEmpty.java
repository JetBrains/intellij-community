// "Convert variable 'res' from String to StringBuilder" "true-preview"

public class Main {
    String test(int[] ints) {
        String res = "";
        for (int i : ints) {
            res = res <caret>+ i + "";
        }
        return res;
    }
}
