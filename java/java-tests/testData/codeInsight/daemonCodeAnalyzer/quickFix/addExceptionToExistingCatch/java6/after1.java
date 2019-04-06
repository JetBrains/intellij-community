// "Replace 'FileNotFoundException' with more generic 'IOException'" "true"
import java.io.*;


public class c1 {
    void f() {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream("");
            DataInputStream dis = new DataInputStream(fis);
            dis.<caret>readInt();
        } catch (IOException e) {
            e.printStackTrace();  
        }
    }
}