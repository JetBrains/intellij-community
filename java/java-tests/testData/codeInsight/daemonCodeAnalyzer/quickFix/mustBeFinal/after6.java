// "Transform 'i' into final one element array" "true"
import java.io.*;

class aa {
 void f() {
     final int[] i = {4};
     Runnable runnable = new Runnable() {
         public void run() {
             new Runnable() {
                 public void run() {
                     int o= i[0];
                     i[0] =o;
                 }
             };
             new Runnable() {
                 public void run() {
                     int o=<caret> i[0];
                 }
             };
         }
     };
 }
}

