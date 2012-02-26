// "Change 'implements Object' to 'extends Object'" "true"
import java.io.*;

class a extends Object implements Runnable,<caret> Serializable {
 public void run() {
 }
}

