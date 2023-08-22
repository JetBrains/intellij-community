// "Change 'extends Runnable' to 'implements Runnable'" "true-preview"
import java.io.*;

class a extends Object,<caret>Runnable implements Serializable {
 public void run() {
 }
}

