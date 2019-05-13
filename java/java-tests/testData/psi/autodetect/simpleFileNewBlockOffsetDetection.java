


import java.lang.Override;
import java.lang.Runnable;



public class T {



  public void test() {
    run(new Runnable() {
        @Override
        public void run() {
          System.out.println("AAAA");
        }
    });
  }



}