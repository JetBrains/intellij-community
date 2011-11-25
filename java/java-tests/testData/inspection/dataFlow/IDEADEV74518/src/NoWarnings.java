import java.util.*;

public class NoWarnings {
    public void f() {
        int i = 1;

        boolean b = true;
        while (true) {
            if (b && i == 1) {
                b = false;
            }
            else {
                i = g();
            }
        }
    }
  
    public int g() {
      return 1;
    }
}
