// "Move catch for 'java.io.IOException' before 'java.io.FileNotFoundException'" "false"
import java.io.*;

class a {
 void f(int i) {
     try {
         int p = 0;
     }
     catch (FileNotFoundException e) {
       int excep = 0;
     }
     catch (<caret>IOException e)  {
       int ioexcep = 0;
     }

 }
}

