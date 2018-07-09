// "Convert variable 'res' from String to StringBuilder (null-safe)" "true"

import java.util.Optional;

public class Main {
    String test(String[] strings) {
        StringBuilder res = null;
        for (String s : strings) {
            if(res == null) {
                res = Optional.ofNullable(s.isEmpty() ? null : s).map(StringBuilder::new).orElse(null);
            } else {
                res.append(", ").append(s);
            }
            res = (res == null ? new StringBuilder("null") : res).append(", ");
            res.append(s);
        }
        System.out.println(res);
        consume(res.toString());
        return res.toString(); // known to be not-null at this point
    }

    // NotNull parameter inferred
    static void consume(String s) {
        System.out.println(s.trim());
    }
}
