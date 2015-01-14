// "Replace with lambda" "true"
class Test {
  String c = null;

  public void main(String[] args){
    invokeLater(new Runna<caret>ble() {
      public void run() {
        c.substring(0).toString();
      }
    });
  }
  
  public void invokeLater(Runnable r) {}
}