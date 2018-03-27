public abstract class Super {

    void foo() {
      if (this instanceof Sub) {
        ((Sub) this).subMethod();<caret>
      }
    }


}

interface Sub extends Zzza {
  void subMethod() {}

}