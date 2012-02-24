// "Change 'implements Object' to 'extends Object'" "false"
import java.io.*;

class a extends b implements Runnable,<caret>Object,Serializable {
 public void run() {
 }
}

class b {}
