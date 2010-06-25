// "Delete catch for 'java.io.IOException'" "true"
import java.io.*;

class a {
 void f(int i) {
     try {
         int p = 0;
     }
     catch (<caret>IOException e)  {
     }
     catch (Exception e)  {
     }

 }
}

