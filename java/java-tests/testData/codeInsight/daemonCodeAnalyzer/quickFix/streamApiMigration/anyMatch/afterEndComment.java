// "Collapse loop with stream 'anyMatch()'" "true-preview"
import java.util.List;

class X {
    String test(List<String> list) {
        if (list.stream().map(String::trim).anyMatch(String::isEmpty)) {
            return null; // Comment
        }
        System.out.println("hello");
        return "foo";
    }
}