// "Replace with anyMatch()" "true"
import java.util.List;

class X {
    String test(List<String> list) {
        for (String s : li<caret>st) {
            String s1 = s.trim();
            if (s1.isEmpty()) {
                return null; // Comment
            }
        }
        System.out.println("hello");
        return "foo";
    }
}