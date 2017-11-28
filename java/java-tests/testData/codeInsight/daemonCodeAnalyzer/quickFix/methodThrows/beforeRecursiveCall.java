// "Remove 'IOException' from 'f' throws list" "true"
import java.io.*;

class a {
 private void f() throws <caret>IOException {
    if (false) f();
 }
}

