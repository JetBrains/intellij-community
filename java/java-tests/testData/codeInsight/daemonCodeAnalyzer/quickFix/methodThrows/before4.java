// "Remove 'IOException' from 'f' throws list" "false"
import java.io.*;

class a {
 private void f() throws <caret>IOException {
   throw new IOException();
 }
}

