// "Convert variable 'res' from String to StringBuilder" "true-preview"

public class Main {
    String test(int[] ints) {
        StringBuilder res = new StringBuilder();
        for (int i : ints) {
            res.append(i);
        }
        return res.toString();
    }
}
