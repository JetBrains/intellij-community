// "Convert variable 'res' from String to StringBuilder" "true"

public class Main {
    String test(String[] strings) {
        /*within*/
        StringBuilder res = new StringBuilder();
        for (String s : strings) {
            if (/*before*/res.length() > 0) {
                res.append(", ");
            }
            if (s.contains("'")) {
                res.append('[' // bracket
                ).append(s).append(']');
            } else {
                res.append(s);
            }
        }
        return res.toString();
    }
}
