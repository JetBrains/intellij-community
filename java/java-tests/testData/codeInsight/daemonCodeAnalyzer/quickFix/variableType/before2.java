// "Change 'i' type to 'java.lang.String'" "true"
import java.io.*;

class a {
 void f() {
   int i;
   <caret>i = "dd";
 }
}

