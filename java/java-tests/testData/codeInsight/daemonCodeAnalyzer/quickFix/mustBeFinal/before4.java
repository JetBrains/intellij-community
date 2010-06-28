// "Transform 'i' into final one element array" "false"
import java.io.*;

class aa {
 void f(int i) {
     new Runnable() {
       public void run() {
         <caret>i = 9;
       }
     };
 }
}

