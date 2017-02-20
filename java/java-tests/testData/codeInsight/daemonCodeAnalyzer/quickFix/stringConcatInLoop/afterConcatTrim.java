// "Convert variable 'res' from String to StringBuilder" "true"

public class Main {
    String test(String[] strings) {
        StringBuilder res = new StringBuilder();
        for (String s : strings) {
            res.append(s);
        }
        res = new StringBuilder(res.toString().trim());
        return (res.length() == 0) ? null : res.toString();
    }
}
