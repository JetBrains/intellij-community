// "Copy 'i' to temp final variable" "true"
import java.io.*;

class a {
 void f() {
     int i = 0;
     new Runnable() {
       public void run() {
         int ii = <caret>i;
       }
     };
     i++;
 }
}

