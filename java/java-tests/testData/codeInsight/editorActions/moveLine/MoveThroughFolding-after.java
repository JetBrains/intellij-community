import javax.swing.*;
import java.lang.Override;
import java.lang.Runnable;

class Test {
    void m() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                System.out.println();
            }
<selection>        System.out.println();
<caret></selection>        });
    }
}