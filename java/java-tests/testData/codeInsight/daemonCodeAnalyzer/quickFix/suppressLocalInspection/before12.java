// "Suppress for class" "false"
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

class Test {
    @javax.annotation.Generated(value = "unknown")
    public static void main(String[] args) {
        ActionListener listener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int <caret>i = 0;
                System.out.println(i);
            }
        };
    }
}