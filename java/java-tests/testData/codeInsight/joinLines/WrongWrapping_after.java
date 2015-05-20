import javax.swing.*;
import java.awt.*;

class Foo {
    void foo (){
        ButtonWithExtension button = new ButtonWithExtension("", "", "", "");
    }
  
  private abstract class MyActionButton extends JComponent{
    protected MyActionButton(String s1, String s2, String s3, String s4) {
    }
  }
  
  private class ButtonWithExtension extends MyActionButton {
    protected ButtonWithExtension(String s1, String s2, String s3, String s4) {
      super(s1, s2, s3, s4);
    }
  }
  
}