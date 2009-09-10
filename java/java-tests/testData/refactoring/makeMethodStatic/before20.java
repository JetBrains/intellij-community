import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

final class Bug
   extends JFrame {
    public Bug() {
        foo();
    }

    public void <caret>foo() {
        addWindowListener(new MyWindowListener());
    }

    private class MyWindowListener
       extends WindowAdapter {
        public void windowActivated(WindowEvent e) {
        }
    }
}