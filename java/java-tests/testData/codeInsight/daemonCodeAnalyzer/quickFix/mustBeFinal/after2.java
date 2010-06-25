// "Copy 'i' to temp final variable" "true"
import java.io.*;

class a {
 void f() {
     int i = 0;
     final int finalI = i;
     new Runnable() {
       public void run() {
         int ii = <caret>finalI;
       }
     };
     i++;
 }
}

