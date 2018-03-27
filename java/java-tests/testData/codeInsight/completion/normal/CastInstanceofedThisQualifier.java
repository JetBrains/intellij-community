public abstract class Super {

    void foo() {
      if (this instanceof Sub) {
        this.subme<caret>
      }
    }


}

interface Sub extends Zzza {
  void subMethod() {}

}