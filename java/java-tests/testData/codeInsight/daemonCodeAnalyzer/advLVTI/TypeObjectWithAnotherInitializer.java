class X {}
class Y {
  public void main(String[] args) {
    var x = new X() {};
    <error descr="Incompatible types. Found: 'Y', required: 'anonymous X'">x = new Y()</error>;
  }
}