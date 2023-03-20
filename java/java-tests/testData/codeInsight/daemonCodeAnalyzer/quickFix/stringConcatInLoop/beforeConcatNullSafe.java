// "Convert variable 'res' from String to StringBuilder (null-safe)" "true-preview"

class Main {
    String test(String[] strings) {
        String res = null;
        for (String s : strings) {
            if(res == null) {
                res = s;
            } else {
                res<caret>+=s;
            }
        }
        return res;
    }
}
