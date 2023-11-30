// "Make 'b' volatile and add Thread.onSpinWait()" "true-preview"
import java.util.*;

public class Test {
    private volatile boolean b;

    public void test() {
        while (b) {
            Thread.onSpinWait();
        }
    }
}