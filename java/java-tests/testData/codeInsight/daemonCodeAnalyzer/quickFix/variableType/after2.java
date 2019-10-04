// "Change variable 'i' type to 'String'" "true"
import java.io.*;

class a {
 void f() {
   String i;
   <caret>i = "dd";
 }
}

