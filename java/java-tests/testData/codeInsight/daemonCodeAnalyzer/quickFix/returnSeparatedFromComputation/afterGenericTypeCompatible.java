// "Move 'return' closer to computation of the value of 'raw'" "true"
import java.util.*;

class T {
    List<String> f(boolean b) {
        if (b) {
            return g();
        }
        return null;
    }

    List<String> g() {
        return Collections.singletonList("");
    }
}