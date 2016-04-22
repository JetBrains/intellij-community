// "Replace with lambda" "true"
enum E {
  A(new Runna<caret>ble() {
    public void run(){}
  });

  public E(Runnable r) {}
}
