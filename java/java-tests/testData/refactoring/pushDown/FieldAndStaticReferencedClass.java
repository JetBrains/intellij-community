public class Test {
  static class <caret>C {
    static BOOL;
  }

  Runnable fieldToMove = new Runnable(){
    public void run(){
       if (C.BOOL){}
    }
  };
}

class B extends Test{}