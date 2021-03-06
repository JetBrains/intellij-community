// "Create method 'getPreloadKeys'" "true"
import java.util.*;

class X {
    void test() {
        Set<String> keys = new HashSet<String>();
        keys.addAll(getPreloadKeys());
    }

    private Collection<String> getPreloadKeys() {
        return null;
    }
}