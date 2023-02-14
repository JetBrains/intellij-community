// "Defer assignment to 'n' using temp variable" "true-preview"
import java.io.*;

class a {
    final int n;
    {
        n=3;
        <caret>n=3;
    }
}
