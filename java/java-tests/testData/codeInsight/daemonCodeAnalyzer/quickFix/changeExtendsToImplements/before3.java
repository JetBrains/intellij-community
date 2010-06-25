// "Change 'implements java.lang.Object' to 'extends java.lang.Object'" "true"
import java.io.*;

class a implements Runnable,<caret>Object,Serializable {
 public void run() {
 }
}

