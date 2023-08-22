// "Convert variable 'res' from String to StringBuilder" "true-preview"

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
