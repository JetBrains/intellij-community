// "Create Field 'panel'" "true"
import javax.swing.*;

class Test {
  void foo(JPanel container) {
    if (p<caret>anel == null) {
      panel = new JPanel();
      panel.setOpaque(true);
      container.add(panel);
    }
  }
}