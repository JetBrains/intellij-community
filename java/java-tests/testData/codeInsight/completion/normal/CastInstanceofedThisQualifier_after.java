public abstract class Super {

    void foo() {
      if (this instanceof Sub) {
        ((Sub) this).subMethod();<caret>
      }
    }


}

class Sub extends Zzza {
  void subMethod() {}

}