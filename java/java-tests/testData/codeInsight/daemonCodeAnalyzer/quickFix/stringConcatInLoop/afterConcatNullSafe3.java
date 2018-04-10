// "Convert variable 'res' from String to StringBuilder (null-safe)" "true"

public class Main {
    String test(String[] strings) {
        StringBuilder res = null;
        res = (res == null ? new StringBuilder("null") : res).append("foo");
        for (String s : strings) {
            res.append(", ");
            res.append(s);
        }
        return res.toString(); // known to be not-null at this point
    }
}
