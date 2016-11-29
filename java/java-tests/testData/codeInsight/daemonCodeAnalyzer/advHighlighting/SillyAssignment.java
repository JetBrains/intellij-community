// silly asignment

class JPanel {
  JPanel getSize() { return this; }
  int height;
}
class a {
  int f;
  JPanel fpanel;

  void f(int i) {

   i = <warning descr="Variable 'i' is assigned to itself">i</warning>;
  }

  void f2() {
    this.f = <warning descr="Variable 'f' is assigned to itself">f</warning>;
    a.this.f = <warning descr="Variable 'f' is assigned to itself">f</warning>;
    f = <warning descr="Variable 'f' is assigned to itself">this.f</warning>;
  }

  void f3(Object o) {
    int i = 0;
    i = <warning descr="Variable 'i' is assigned to itself">i</warning>;
    i = (int)<warning descr="Variable 'i' is assigned to itself">i</warning>;
    o = ((Object)(<warning descr="Variable 'o' is assigned to itself">o</warning>));
    Object o1 = o = ((Object)(<warning descr="Variable 'o' is assigned to itself">o</warning>));
    System.out.println(o1);
    o = (double)o;
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

  void m() {
    double m = 1.5;
    m = (double) (int) m;
  }
}
