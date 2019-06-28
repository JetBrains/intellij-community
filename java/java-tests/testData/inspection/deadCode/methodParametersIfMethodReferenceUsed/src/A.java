import javax.swing.*;
import java.awt.event.ActionEvent;

class A {
    {
        new JComboBox().addActionListener(this::handleActionEvent);
    }

    public static void main(String[] args) {
        new A();
    }
    private void handleActionEvent(ActionEvent ignore) {
        System.out.println(123);
    }
}
