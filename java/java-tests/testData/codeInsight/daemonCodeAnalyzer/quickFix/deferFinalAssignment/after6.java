// "Defer assignment to 'n' using temp variable" "true-preview"
import java.io.*;

class a {
    final int n;
    {
        int n1<caret>;
        n1 =3;
        n1 =3;
        n = n1;
    }
}
