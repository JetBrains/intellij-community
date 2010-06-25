// "Defer assignment to 'n' using temp variable" "true"
import java.io.*;

class a {
    final int n;
    a(int n) {
        int n1;
        n1 =n;
        <caret>n1 =n;
        this.n = n1;
    }
}
