// "Remove 'IOException' from 'f()' throws list" "true-preview"
import java.io.*;

class a {
 private void f() throws <caret>IOException {
 }
}

