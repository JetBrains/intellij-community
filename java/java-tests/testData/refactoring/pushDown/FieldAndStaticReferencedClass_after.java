public class Test {

}

class B extends Test{
    Runnable fieldToMove = new Runnable(){
      public void run(){
         if (C.BOOL){}
      }
    };

    static class C {
      static BOOL;
    }
}