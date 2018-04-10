// "Convert variable 'res' from String to StringBuilder (null-safe)" "true"

public class Main {
    String test(String[] strings) {
        String res = null;
        for (String s : strings) {
            if(res == null) {
                res = s.isEmpty() ? null : s;
            } else {
                res<caret>+=", "+s;
            }
            res+=", ";
            res+=s;
        }
        System.out.println(res);
        consume(res);
        return res; // known to be not-null at this point
    }

    // NotNull parameter inferred
    static void consume(String s) {
        System.out.println(s.trim());
    }
}
