// "Remove 'IOException' from 'f()' throws list" "true-preview"
import java.io.*;

class a {
 void f() throws IOException {
 }
}

class b extends a {
 void f() {
 }
}

