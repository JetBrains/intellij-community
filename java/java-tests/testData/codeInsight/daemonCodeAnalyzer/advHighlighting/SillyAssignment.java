// silly asignment

class JPanel {
  JPanel getSize() { return this; }
  int height;
}
class a {
  int f;
  JPanel fpanel;

  void f(int <text_attr descr="Reassigned parameter">i</text_attr>) {

   <text_attr descr="Reassigned parameter">i</text_attr> = <warning descr="Variable 'i' is assigned to itself"><text_attr descr="Reassigned parameter">i</text_attr></warning>;
  }

  void f2() {
    this.f = <warning descr="Variable 'f' is assigned to itself">f</warning>;
    a.this.f = <warning descr="Variable 'f' is assigned to itself">f</warning>;
    f = <warning descr="Variable 'f' is assigned to itself">this.f</warning>;
  }

  void f3(Object <text_attr descr="Reassigned parameter">o</text_attr>) {
    int <text_attr descr="Reassigned local variable">i</text_attr> = 0;
    <text_attr descr="Reassigned local variable">i</text_attr> = <warning descr="Variable 'i' is assigned to itself"><text_attr descr="Reassigned local variable">i</text_attr></warning>;
    <text_attr descr="Reassigned local variable">i</text_attr> = (int)<warning descr="Variable 'i' is assigned to itself"><text_attr descr="Reassigned local variable">i</text_attr></warning>;
    <text_attr descr="Reassigned parameter">o</text_attr> = ((Object)(<warning descr="Variable 'o' is assigned to itself"><text_attr descr="Reassigned parameter">o</text_attr></warning>));
    Object o1 = <text_attr descr="Reassigned parameter">o</text_attr> = ((Object)(<warning descr="Variable 'o' is assigned to itself"><text_attr descr="Reassigned parameter">o</text_attr></warning>));
    System.out.println(o1);
    <text_attr descr="Reassigned parameter">o</text_attr> = (double)<text_attr descr="Reassigned parameter">o</text_attr>;
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
    double <text_attr descr="Reassigned local variable">m</text_attr> = 1.5;
    <text_attr descr="Reassigned local variable">m</text_attr> = (double) (int) <text_attr descr="Reassigned local variable">m</text_attr>;
  }
}
