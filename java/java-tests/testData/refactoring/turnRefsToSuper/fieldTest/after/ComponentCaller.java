import javax.swing.*;
import java.awt.*;
import javax.swing.*;
 
public class ComponentCaller extends JComponent{
    Component1 component1;
 
    public ComponentCaller() {
        component1  = new Component1();
        buildUI();
    }
 
    private void buildUI() {
        setLayout(new BorderLayout());
        add(new JScrollPane(component1));
    }
 
    public void doSomething(){
        component1.doSomething();
    }
}
