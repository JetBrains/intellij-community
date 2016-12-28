// "Convert variable 'res' from String to StringBuilder" "true"

public class Main {
    String test(String[] strings) {
        StringBuilder res = new StringBuilder();
        for (String s : strings) {
            if (res.length() > 0) {
                res.insert(0, ".");
            }
            res.insert(0, s);
        }
        return res.toString();
    }
}
