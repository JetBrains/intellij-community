// "Convert variable 'res' from String to StringBuilder" "true-preview"

public class Main {
    String test(String[] strings) {
        String res = "";
        for (String s : strings) {
            res <caret>+= s + "\n";
            res += s;
        }
        return res;
    }
}
