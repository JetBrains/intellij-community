// "Move 'return' closer to computation of the value of 'n'" "true-preview"
import java.util.*;

class T {
    List foo(boolean b) {
        List n = new ArrayList();
        if (b)
            n = null;
        <caret>return n;
    }
}