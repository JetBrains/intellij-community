// "Replace with lambda" "true-preview"
class Test {
  String c = null;

  public void main(String[] args){
    invokeLater(() -> c.substring(0).toString());
  }
  
  public void invokeLater(Runnable r) {}
}