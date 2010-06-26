// "Suppress for method" "true"
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

class Test {
    public static void main(String[] args) {
        ActionListener listener = new ActionListener() {
            /** @noinspection LocalCanBeFinal*/
            public void actionPerformed(ActionEvent e) {
                int i = 0;
                System.out.println(i);
            }
        };
    }
}