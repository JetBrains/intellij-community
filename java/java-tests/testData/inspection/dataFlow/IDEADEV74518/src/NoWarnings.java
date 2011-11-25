import java.util.*;

public class NoWarnings {
    public void f() {
        int i = 1;

        boolean b = true;
        while (i < 200) {
            if (b && i == 1) { // Warning here: i == 1 is always true, but it is not so.
                b = false;
            } else {
                i++;
            }
        }
    }
}
