// "Transform 'i' into final one element array" "true"
import java.io.*;

class aa {
 void f() {
     int i = 4;
     Runnable runnable = new Runnable() {
         public void run() {
             new Runnable() {
                 public void run() {
                     int o=i;
                     i=o;
                 }
             };
             new Runnable() {
                 public void run() {
                     int o=<caret>i;
                 }
             };
         }
     };
 }
}

