// "Remove 'IOException' from 'f' throws list" "true"
import java.io.*;

class a {
 void f() {
 }
}

class b extends a {
 void f() <caret>{
 }
}

