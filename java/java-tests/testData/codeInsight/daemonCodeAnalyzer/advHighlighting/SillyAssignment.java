// silly asignment

class JPanel {
  JPanel getSize() { return this; }
  int height;
}
class a {
  int f;
  JPanel fpanel;

  void f(int <info descr="Reassigned parameter">i</info>) {

   <info descr="Reassigned parameter">i</info> = <info descr="Reassigned parameter"><warning descr="Variable 'i' is assigned to itself">i</warning></info>;
  }

  void f2() {
    this.f = <warning descr="Variable 'f' is assigned to itself">f</warning>;
    a.this.f = <warning descr="Variable 'f' is assigned to itself">f</warning>;
    f = <warning descr="Variable 'f' is assigned to itself">this.f</warning>;
  }

  void f3(Object <info descr="Reassigned parameter">o</info>) {
    int <info descr="Reassigned local variable">i</info> = 0;
    <info descr="Reassigned local variable">i</info> = <info descr="Reassigned local variable"><warning descr="Variable 'i' is assigned to itself">i</warning></info>;
    <info descr="Reassigned local variable">i</info> = (int)<info descr="Reassigned local variable"><warning descr="Variable 'i' is assigned to itself">i</warning></info>;
    <info descr="Reassigned parameter">o</info> = ((Object)(<info descr="Reassigned parameter"><warning descr="Variable 'o' is assigned to itself">o</warning></info>));
    Object o1 = <info descr="Reassigned parameter">o</info> = ((Object)(<info descr="Reassigned parameter"><warning descr="Variable 'o' is assigned to itself">o</warning></info>));
    System.out.println(o1);
    <info descr="Reassigned parameter">o</info> = (double)<info descr="Reassigned parameter">o</info>;
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
    double <info descr="Reassigned local variable">m</info> = 1.5;
    <info descr="Reassigned local variable">m</info> = (double) (int) <info descr="Reassigned local variable">m</info>;
  }
}
