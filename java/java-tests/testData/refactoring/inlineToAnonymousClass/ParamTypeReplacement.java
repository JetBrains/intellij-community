public class Simple implements Runnable{
  void foo(Simple s) {
    System.out.println(s.toString());
    s.run();
  }

  public void run(){
  }
}

class Usage {
  Simple s = new Sim<caret>ple();
}