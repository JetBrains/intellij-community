// "Change variable 'i' type to 'String'" "true"
import java.io.*;

class a {
 void f() {
   int i;
   <caret>i = "dd";
 }
}

