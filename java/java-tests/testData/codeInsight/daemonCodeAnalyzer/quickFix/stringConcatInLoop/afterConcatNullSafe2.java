// "Convert variable 'res' from String to StringBuilder (null-safe)" "true"

public class Main {
    String test(String[] strings) {
        StringBuilder res = null;
        for (String s : strings) {
            if (s == null) continue;
            if(res == null) {
                res = new StringBuilder(s);
            } else {
                res.append(s);
            }
        }
        return res == null ? null : res.toString();
    }
}
