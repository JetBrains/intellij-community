// "Transform 'i' into final one element array" "true-preview"
import java.io.*;

class a {
 void f() {
     int i = 0;
     new Runnable() {
       public void run() {
          <caret>i = 0;
       }
     };
 }
}

