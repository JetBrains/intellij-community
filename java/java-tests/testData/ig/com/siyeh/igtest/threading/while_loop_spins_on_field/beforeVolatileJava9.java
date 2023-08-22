// "Add Thread.onSpinWait()" "true-preview"
import java.util.*;

public class Test {
    private volatile boolean b;

    public void test() {
        whi<caret>le (b);
    }
}