import java.lang.String;

class X {
  void m() {

    boolean b = "asd".substring(1).compareTo("qwe".substring(2)) !<caret>= 0;

  }
}