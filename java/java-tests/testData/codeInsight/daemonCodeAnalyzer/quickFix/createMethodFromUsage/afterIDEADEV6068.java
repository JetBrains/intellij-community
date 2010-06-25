// "Create Method 'toMulti'" "true"
import java.util.Map;

class BrokenCreateMethod {

    public void foo(Map<String, String> bar) {
        Map<String, String[]> multiBar = toMulti(bar);
    }

    private Map<String, String[]> toMulti(Map<String, String> bar) {
        <selection>return null;  //To change body of created methods use File | Settings | File Templates.</selection>
    }
}
