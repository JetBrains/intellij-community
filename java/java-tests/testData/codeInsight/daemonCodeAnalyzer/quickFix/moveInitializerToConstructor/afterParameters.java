// "Move initializer to constructor" "true"
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
public class XXX {
    public final ActionListener listener<caret>;

    public XXX() {
        listener = new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    XXX.doit(e);
                }
            };
    }

    private static void doit(ActionEvent e) {
    }
}
