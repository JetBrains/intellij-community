// "Convert variable 'res' from String to StringBuilder" "true"

public class Main {
    String test(String[] strings) {
        String res = "";
        for (String s : strings) {
            if (/*before*/!res/*within*/.isEmpty()) {
                res += ", ";
            }
            if (s.contains("'")) {
                res = (res <caret>+ '[' // bracket
                                  + s + ']');
            } else {
                res = (res + s);
            }
        }
        return res;
    }
}
