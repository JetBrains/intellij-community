// "Remove 'java.io.IOException' from 'f' throws list" "false"
import java.io.*;

class a {
 void f() throws IOException {
 }
}

class b extends a {
 void f() throws <caret>IOException {
 }
}

