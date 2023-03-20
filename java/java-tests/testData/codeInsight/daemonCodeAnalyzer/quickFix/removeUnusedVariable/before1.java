// "Remove local variable 'i'" "true-preview"
import java.io.*;

class a {
 public void run() {
    int <caret>i = 0;
    i++;
    i++;
 }
}

