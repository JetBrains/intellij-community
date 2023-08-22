import javax.swing.*;
class Test {
  String c = null;

  public void main(String[] args){
    SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
            c.substring(0).toString();
        }
    });
  }
}