import javax.swing.*;
import java.awt.*;

class Xc extends JComponent {
    JComponent component;

    protected void paintComponent(final Graphics g) {
        component.<caret>paintComponent(g);
    }
}
