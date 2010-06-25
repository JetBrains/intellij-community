// "Move catch for 'java.io.IOException' before 'java.lang.Exception'" "true"
import java.io.*;

class a {
 void f(int i) {
     try {
         if (i==0) throw new IOException();
     } catch (Exception e) {
       int excep = 0;
     } catch (<caret>IOException e)  {
       int ioexcep = 0;
     }

 }
}

