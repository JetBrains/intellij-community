// "Transform 'i' into final one element array" "true"
import java.io.*;

class aa {
 void f() {
     final int[] i = new int[1];
     Runnable runnable = new Runnable() {
         public void run() {
             <caret>i[0] = 5;
         }
     };
    int f = i[0];
 }
}

