// "Create Method 'toMulti'" "true"
import java.util.Map;

class BrokenCreateMethod {

    public void foo(Map<String, String> bar) {
        Map<String, String[]> multiBar = <caret>toMulti(bar);
    }
}
