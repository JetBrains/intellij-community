// "Change type of 'res' to StringBuilder" "true"

public class Main {
    String test(String[] strings) {
        StringBuilder res = new StringBuilder();
        for (String s : strings) {
            res.append(s);
        }
        return res.toString();
    }
}
