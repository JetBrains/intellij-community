// "Convert variable 'res' from String to StringBuilder (null-safe)" "true"

class Main {
    String test(String[] strings) {
        StringBuilder res = null;
        for (String s : strings) {
            if(res == null) {
                res = s == null ? null : new StringBuilder(s);
            } else {
                res.append(s);
            }
        }
        return res == null ? null : res.toString();
    }
}
