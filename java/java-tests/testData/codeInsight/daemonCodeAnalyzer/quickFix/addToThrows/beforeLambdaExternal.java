// "Add exception to method signature" "false"
class C {

  public static void main(String[] args) throws InterruptedException {
    new Thread(( ) -> {
      Thread.sl<caret>eep(2000);
    }).start();
  }
}
