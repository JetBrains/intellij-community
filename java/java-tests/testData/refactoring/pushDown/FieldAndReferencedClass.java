public class Test {
  class <caret>C {
    BOOL;
  }

  Runnable fieldToMove = new Runnable(){
    public void run(){
       if (new C().BOOL){}
    }
  };
}

class B extends Test{}