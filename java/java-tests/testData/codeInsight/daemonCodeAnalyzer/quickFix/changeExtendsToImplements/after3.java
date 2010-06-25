// "Change 'implements java.lang.Object' to 'extends java.lang.Object'" "true"
import java.io.*;

class a extends Object implements Runnable,<caret> Serializable {
 public void run() {
 }
}

