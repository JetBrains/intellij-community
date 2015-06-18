import javax.swing.*;
import java.awt.*;
class OuterClass  {
    private MyComp myComp;

    private class MyComp extends JPanel {
        @Override
        public void paint(Graphics g) {
            int a = myComp.get<caret>Height();
        }
    }
}