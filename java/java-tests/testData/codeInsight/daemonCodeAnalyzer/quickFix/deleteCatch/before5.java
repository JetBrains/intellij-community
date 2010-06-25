// "Delete catch for 'java.io.IOException'" "true"
import java.io.*;

class a {
 void f(int i) {
     try {
         // comm1
         int p = 0;

         // comm2

     }
     catch (<caret>IOException e)  {
     }

 }
}

