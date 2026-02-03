// "Change 'implements Object' to 'extends Object'" "true-preview"
import java.io.*;

class a extends Object implements Runnable,<caret> Serializable {
 public void run() {
 }
}

