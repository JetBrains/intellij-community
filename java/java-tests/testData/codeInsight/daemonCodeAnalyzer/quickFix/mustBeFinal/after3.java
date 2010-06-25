// "Transform 'i' into final one element array" "true"
import java.io.*;

class a {
 void f() {
     final int[] i = {0};
     new Runnable() {
       public void run() {
          <caret>i[0] = 0;
       }
     };
 }
}

