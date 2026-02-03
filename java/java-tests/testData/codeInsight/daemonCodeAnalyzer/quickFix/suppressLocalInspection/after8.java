// "Suppress for class" "true"
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/** @noinspection LocalCanBeFinal*/
class Test {
    public static void main(String[] args) {
        ActionListener listener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int <caret>i = 0;
                System.out.println(i);
            }
        };
    }
}