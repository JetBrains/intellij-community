// silly asignment
import javax.swing.*;

class a {
  int f;
  JPanel fpanel;

  void f(int i) {

   <warning descr="Variable is assigned to itself">i = i</warning>;
  }

  void f2() {
    <warning descr="Variable is assigned to itself">this.f = f</warning>;
    <warning descr="Variable is assigned to itself">a.this.f = f</warning>;
    <warning descr="Variable is assigned to itself">f = this.f</warning>;
  }

  void f3(Object o) {
    int i = 0;
    <warning descr="Variable is assigned to itself">i = i</warning>;
    <warning descr="Variable is assigned to itself">i = (int)i</warning>;
    <warning descr="Variable is assigned to itself">o = ((Object)(o))</warning>;
  }
  void f4() {
    fpanel.getSize().height = this.fpanel.getSize().height; // not silly. Are you sure you can bet getSize() has no side effects? 
  }

  void cf1() {
    JPanel a = new JPanel(), b = new JPanel();
    a.getSize().height = b.getSize().height; // not silly!
  }

  void cf2(a aa) {
    aa.f = f;
  }
}
