// "Change 'i' type to 'java.lang.String'" "true"
import java.io.*;

class a {
 void f() {
   String i;
   <caret>i = "dd";
 }
}

