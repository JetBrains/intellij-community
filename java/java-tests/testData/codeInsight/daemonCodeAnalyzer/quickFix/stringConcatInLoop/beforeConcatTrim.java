// "Change type of 'res' to StringBuilder" "true"

public class Main {
    String test(String[] strings) {
        String res = "";
        for (String s : strings) {
            res <caret>+= s;
        }
        res = res.trim();
        return res.isEmpty() ? null : res;
    }
}
