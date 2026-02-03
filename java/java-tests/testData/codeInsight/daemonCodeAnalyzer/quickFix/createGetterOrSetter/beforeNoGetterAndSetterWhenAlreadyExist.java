// "Create getter and setter for 's'" "false"
class A<T> {
  private T <caret>s;

  T getS() {
    return s;
  }

  void setS(T s) {
    this.s = s;
  }
}