// "Create method 'toMulti'" "true-preview"
import java.util.Map;

class BrokenCreateMethod {

    public void foo(Map<String, String> bar) {
        Map<String, String[]> multiBar = toMulti(bar);
    }

    private Map<String, String[]> toMulti(Map<String, String> bar) {
        <selection>return null;</selection>
    }
}
