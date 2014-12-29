package x;
import java.io.*;

<warning descr="Default File template">/**
 * Created by Alexey on 02.12.2005.
 */</warning>
public class X implements Runnable{
    File f; //kkj lkkl jjkuufdffffjkkjjh kjh kjhj kkjh kjh  i k kj kj klj lkj lkj lkjl kj klkl kl
    {
        try {
            f=null;
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            g();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void g() throws EOFException, FileNotFoundException {
        
    }

    public int hashCode() {
        return super.hashCode();
    }
        
    public String toString() {
     return super.toString();
    }

    public void run() {
      
    }

    private void method() {
        // hello
    }
    private int geti() {
        return 0;
    }
    private void cat() {
      try {
      }
      catch (Exception e) {
       e.printStackTrace(); // hey
      }
    }
     protected void finalize() throws Throwable {
            super.finalize();    
     }

    private void multi() {
        try {
            g();
        } catch (EOFException | FileNotFoundException ex) {
            ex.printStackTrace();
        }
    }
}
