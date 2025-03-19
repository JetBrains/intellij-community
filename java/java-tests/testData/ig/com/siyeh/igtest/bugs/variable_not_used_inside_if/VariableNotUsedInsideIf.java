



public class VariableNotUsedInsideIf {

  void foo(String s) {
    if (s == null) {
      s = "foo";
    } else {
      System.out.println("not null");
    }
    System.out.println(s);
  }

  void bar(String s) {
    if (s == null) {
      return;
    }
  }

  void bas(String s) {
    if (s == null) {
      s = "";
    }
    System.out.println(s);
  }

  void bat(String s) {
    if (<warning descr="'s' checked for 'null' is not used inside 'if'">s</warning> != null) {
      System.out.println();
    }
    if (<warning descr="'s' checked for 'null' is not used inside 'if'">s</warning> == null) {

    } else {
      
    }
  }

  void money(String s) {
    if (((<warning descr="'s' checked for 'null' is not used inside 'if'">s</warning>) != (null))) {
      System.out.println();
    }
  }

  void x(Integer x){
    if (<warning descr="'x' checked for 'null' is not used inside 'if'">x</warning> != null) {
      System.out.println();
    }
  }

  int x(Integer x, int y){
    if (<warning descr="'x' checked for 'null' is not used inside 'if'">x</warning> != null) return y;//oops, wrong one
    return y;
  }

  int conditional(Integer x) {
    return <warning descr="'x' checked for 'null' is not used inside conditional">x</warning> == null ? 1 : someValue();
  }

  private int someValue() {
    return 0;
  }

  void perenthesis(String[] args)
  {
    String message = (<warning descr="'args' checked for 'null' is not used inside conditional">args</warning> == null) ? "not null" : "null";
  }
}