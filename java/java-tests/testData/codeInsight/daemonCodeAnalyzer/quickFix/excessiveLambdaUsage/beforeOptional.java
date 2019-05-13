// "Use 'orElse' method without lambda" "true"
import java.util.*;

class Test {
    public String test(List<String> data) {
        return data.stream().filter(Objects::nonNull).findFirst().orElseGet((<caret>) -> null);
    }
}