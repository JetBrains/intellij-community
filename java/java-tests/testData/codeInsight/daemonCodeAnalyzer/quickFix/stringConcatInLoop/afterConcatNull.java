// "Convert variable 'res' from String to StringBuilder" "true-preview"

public class Main {
    String test(String[] strings) {
        StringBuilder res = null;
        for (String s : strings) {
            if(res == null) {
                res = new StringBuilder(s);
            } else {
                res.append(s);
            }
        }
        return res.toString();
    }
}
