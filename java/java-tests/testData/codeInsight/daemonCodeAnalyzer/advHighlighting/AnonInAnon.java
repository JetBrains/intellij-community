public class QQQ {
  {
    new Outer(){
      void foo(){
        new Inner(){
          void method() {
            super.method();
          }
        };
      }
    };
  }
}

class Outer{
  class Inner{
    void method(){

    }
  }
}
