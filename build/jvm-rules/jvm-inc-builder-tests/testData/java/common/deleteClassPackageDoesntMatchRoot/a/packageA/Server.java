/**
 * created at Jan 22, 2002
 * @author Jeka
 */
package ppp;

public class Server {
  public void method() {
    new Runnable(){
      public void run() {
        System.out.println("Server.method");
      }
    }.run();
  }
}
