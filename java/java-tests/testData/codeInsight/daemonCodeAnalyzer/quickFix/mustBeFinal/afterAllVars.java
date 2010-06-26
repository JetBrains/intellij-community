// "Make variables final" "true"
import java.io.*;

class a {
 void f() {
     final int i = 0;
     final int j = 0;
     new Runnable() {
       public void run() {
         int ii = <caret>i + j;
       }
     };
 }
}

