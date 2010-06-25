// "Transform 'i' into final one element array" "true"
import java.io.*;

class aa {
 void f() {
     final int[] i = {9};
     new Runnable() {
       public void run() {
         int p = i[0];
       }
     };
     new Runnable() {
       public void run() {
         i[0] = 0;
       }
     };
 }
}

