import javax.swing.*;
class Test {
  String c = null;

  public void main(String[] args){
    SwingUtilities.invokeLater((<caret>) -> c.substring(0).toString());
  }
}