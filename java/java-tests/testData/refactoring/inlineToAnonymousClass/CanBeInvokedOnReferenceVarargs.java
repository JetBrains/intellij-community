public class Simple implements Runnable {
  public void run(){}
}

class Usage {
  void foo() {
    bar(new Si<caret>mple());
  }

  void bar(Runnable... r){}
}