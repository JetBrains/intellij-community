// "Create Field 'panel'" "true"
import javax.swing.*;

class Test {
    private JPanel panel;

    void foo(JPanel container) {
    if (panel == null) {
      panel = new JPanel();
      panel.setOpaque(true);
      container.add(panel);
    }
  }
}