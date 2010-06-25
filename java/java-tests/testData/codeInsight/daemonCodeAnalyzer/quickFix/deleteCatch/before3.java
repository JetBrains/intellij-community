// "Delete catch for 'java.io.IOException'" "false"
import java.io.*;

class a {
 void f(int i) {
     try {
         int p = 0;
         throw new IOException();
     }
     catch (<caret>IOException e)  {
     }
     catch (Exception e)  {
     }

 }
}

