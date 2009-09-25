public class Test {
  public Runnable foo(final int i) {
    return new Runnable() {
      private int myInt = i;      
      public void run(){
      }
    }
  }
}