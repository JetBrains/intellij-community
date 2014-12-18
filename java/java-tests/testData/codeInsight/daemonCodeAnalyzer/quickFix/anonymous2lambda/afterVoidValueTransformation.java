// "Replace with lambda" "true"
import javax.swing.*;
class Test {
  String c = null;

  public void main(String[] args){
    SwingUtilities.invokeLater(() -> c.substring(0).toString());
  }
}