import java.awt.event.*;

class A {
    private Object b = new Inner();

    public class <caret>Inner extends Throwable implements Runnable {
        public void run() {
        }
    }
}