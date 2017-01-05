// "Move 'return' closer to computation of the value of 'raw'" "true"
import java.util.*;

class T {
    List<String> f(boolean b) {
        List raw = null;
        if (b) {
            return g();
        }
        return raw;
    }

    List<String> g() {
        return Collections.singletonList("");
    }
}