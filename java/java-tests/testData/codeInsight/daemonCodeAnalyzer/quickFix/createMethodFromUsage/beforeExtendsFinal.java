// "Create method 'getPreloadKeys'" "true-preview"
import java.util.*;

class X {
    void test() {
        Set<String> keys = new HashSet<String>();
        keys.addAll(<caret>getPreloadKeys());
    }
}