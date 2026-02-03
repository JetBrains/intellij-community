// "Change 'extends Runnable' to 'implements Runnable'" "true-preview"
import java.io.*;

class a extends Object<caret> implements Serializable, Runnable {
 public void run() {
 }
}

