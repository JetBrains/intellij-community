// "Move 'return' closer to computation of the value of 'n'" "true-preview"
import java.util.*;

class T {
    List foo(int k) {
        List n = new ArrayList();
        if (k == 1)
            n = new ArrayList(1);
        if (k == 2)
            n = new ArrayList(2);
        <caret>return n;
    }
}