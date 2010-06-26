// "Suppress for class" "true"
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
class Test {
    public static void main(String[] args) {

        class T {
          void foo() {
            int <caret>i = 0;
            System.out.println(i);
          }
        }
    }
}