// "Replace Optional.isPresent() condition with functional style expression" "true"

import java.util.*;

public class Main {
    public void testOptional(Optional<String> str) {
        String val;
        // line comment
        // another line comment
        /* block comment */
        /*block comment*/
        //before trim
        val = str.map(String::trim).orElse("");
        System.out.println(val);
    }
}