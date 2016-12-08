// "Change type of 'res' to StringBuilder" "true"

public class Main {
    String test(String[] strings) {
        String res = "";
        for (String s : strings) {
            if (!res.isEmpty()) {
                res = "." + res;
            }
            res = s <caret>+ res;
        }
        return res;
    }
}
