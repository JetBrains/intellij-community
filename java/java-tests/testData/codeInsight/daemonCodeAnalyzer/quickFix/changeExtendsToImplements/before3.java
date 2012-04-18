// "Change 'implements Object' to 'extends Object'" "true"
import java.io.*;

class a implements Runnable,<caret>Object,Serializable {
 public void run() {
 }
}

