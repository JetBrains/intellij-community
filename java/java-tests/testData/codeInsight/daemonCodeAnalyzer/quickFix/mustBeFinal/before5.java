// "Transform 'i' into final one element array" "true"
import java.io.*;

class aa {
 void f() {
     int i=9;
     new Runnable() {
       public void run() {
         int p = <caret>i;
       }
     };
     new Runnable() {
       public void run() {
         i = 0;
       }
     };
 }
}

