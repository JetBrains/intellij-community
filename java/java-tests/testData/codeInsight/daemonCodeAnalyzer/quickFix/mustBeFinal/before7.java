// "Transform 'i' into final one element array" "true"
import java.io.*;

class aa {
 void f() {
     int i;
     Runnable runnable = new Runnable() {
         public void run() {
             <caret>i = 5;
         }
     };
    int f = i;
 }
}

