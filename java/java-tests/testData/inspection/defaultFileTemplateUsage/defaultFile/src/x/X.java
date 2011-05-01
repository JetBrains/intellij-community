package x;
import java.io.*;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey
 * Date: 02.12.2005
 * Time: 0:24:14
 * To change this template use File | Settings | File Templates.
 */
public class X implements Runnable{
    File f; //kkj lkkl jjkuufdffffjkkjjh kjh kjhj kkjh kjh  i k kj kj klj lkj lkj lkjl kj klkl kl
    {
        try {
            f=null;
        } catch (Exception e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        try {
            g();
        } catch (Exception ex) {
            ex.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    private void g() throws EOFException, FileNotFoundException {
        //To change body of created methods use File | Settings | File Templates.
    }

    public int hashCode() {
        return super.hashCode();    //To change body of overridden methods use File | Settings | File Templates.
    }
        
    public String toString() {
     return super.toString();            //To change body of overridden methods use File | Settings | File Templates.
    }

    public void run() {
        //To change body of implemented methods use File | Settings | File Templates.
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
            ex.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }
}

