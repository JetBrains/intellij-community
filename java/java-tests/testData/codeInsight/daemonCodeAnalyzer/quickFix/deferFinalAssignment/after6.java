// "Defer assignment to 'n' using temp variable" "true"
import java.io.*;

class a {
    final int n;
    {
        int n1;
        n1 =3;
        <caret>n1 =3;
        n = n1;
    }
}
