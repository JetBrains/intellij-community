public abstract class Super {

    void foo() {
      if (this instanceof Sub) {
        this.subme<caret>
      }
    }


}

class Sub extends Zzza {
  void subMethod() {}

}