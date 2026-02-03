// "Defer assignment to 'n' using temp variable" "true-preview"
import java.io.*;

class a {
    final int n;
    a(int n) {
        this.n=n;
        <caret>this.n=n;
    }
}
