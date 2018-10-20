import java.util.*;

class NoWarnings {
    public void f() {
        int i = 1;

        boolean b = true;
        while (true) {
            if (b && <warning descr="Condition 'i == 1' is always 'true' when reached">i == 1</warning>) {
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
