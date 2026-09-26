// "Defer assignment to 'x' using temp variable" "false"
import java.io.*;

class a {
    final int x;
    
    a(int _x) {
        x = _x;
    }
    
    a() {
        this(0);
        <caret>x = 1;
    }
}

