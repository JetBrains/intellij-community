import com.intellij.ui.ScrollPaneFactory;

import javax.swing.*;
import java.awt.*;
 
public class ComponentCaller extends JComponent{
    Component1 component1;
 
    public ComponentCaller() {
        component1  = new Component1();
        buildUI();
    }
 
    private void buildUI() {
        setLayout(new BorderLayout());
      add(ScrollPaneFactory.createScrollPane((Component)component1));
    }
 
    public void doSomething(){
        component1.doSomething();
    }
}
