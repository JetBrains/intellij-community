// "Change type of 'res' to StringBuilder" "true"

public class Main {
    String test(String[] strings) {
        String res = "";
        for (String s : strings) {
            if (/*before*/!res/*within*/.isEmpty()) {
                res += ", ";
            }
            res <caret>+= s;
        }
        return res;
    }
}
