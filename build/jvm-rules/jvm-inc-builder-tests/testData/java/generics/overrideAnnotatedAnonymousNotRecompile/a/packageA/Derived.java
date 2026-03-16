package packageA;

public class Derived extends Base {

  public void bar() {
    Runnable base = new RunnableAdapter() {
      @Override
      public void run() {
      }
    };
  }
  
//  public void foo(String... params) {
//   System.out.println("Derived " + params.toString());
//  }
}
