import javax.swing.*;

class Test {
    public static JComponent getFoo() {
        JComponent c = new JPanel();
        return <caret>c;
    }
}