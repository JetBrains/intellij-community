// "Move 'return' closer to computation of the value of 'n'" "true"
import java.util.*;

class T {
    List foo(int k) {
        List n = new ArrayList();
        if (k == 1)
            n = new ArrayList(1);
        if (k == 2)
            return new ArrayList(2);
        return n;
    }
}