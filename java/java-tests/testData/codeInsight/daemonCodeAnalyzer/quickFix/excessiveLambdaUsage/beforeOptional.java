// "Use 'orElse' method without lambda" "true-preview"
import java.util.*;

class Test {
    public String test(List<String> data) {
        return data.stream().filter(Objects::nonNull).findFirst().orElseGet((<caret>) -> null);
    }
}