// "Convert variable 'res' from String to StringBuilder (null-safe)" "true"

public class Main {
    String test(String[] strings) {
        String res = null;
        res += "foo";
        for (String s : strings) {
            res+<caret>=", ";
            res+=s;
        }
        return res; // known to be not-null at this point
    }
}
