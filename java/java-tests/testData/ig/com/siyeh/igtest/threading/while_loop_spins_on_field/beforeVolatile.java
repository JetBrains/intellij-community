// "Fix all ''while' loop spins on field' problems in file" "false"
import java.util.*;

public class Test {
    private volatile boolean b;

    public void test() {
        whi<caret>le (b);
    }
}