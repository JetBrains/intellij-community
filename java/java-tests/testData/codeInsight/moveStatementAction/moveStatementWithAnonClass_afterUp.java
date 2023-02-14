import javax.swing.*;
import java.util.List;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
/**
 * XXX
 */
class CL {
    public static void main(String[] args) {
        JButton a = new JButton (), b = new JButton (), c=new JButton();
        <caret>a.addActionListener (new ActionListener(){ //<<------ WON'T GO UP !!
            public void actionPerformed (ActionEvent e2) { }
        });//2
        b.addActionListener (new ActionListener(){ //<<------ WON'T GO DOWN !!
            public void actionPerformed (ActionEvent e1) { }
        });//1
        c.addActionListener (new ActionListener(){ //down here
            public void actionPerformed (ActionEvent e3) { }
        });//3

    }
}


