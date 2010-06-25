// "Change 'extends java.lang.Runnable' to 'implements java.lang.Runnable'" "true"
import java.io.*;

class a extends Object,<caret>Runnable implements Serializable {
 public void run() {
 }
}

