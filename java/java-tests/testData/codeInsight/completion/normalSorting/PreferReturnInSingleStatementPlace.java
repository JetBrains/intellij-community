import javax.swing.*;

class A extends JPanel {
    void f() {
        // "return"
        if (true) re<caret>

        // "continue"
        for (int i=0;i<10;i++) {
        }
    }
}