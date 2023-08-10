// "Make 'x()' return 'C'" "false"

public class X {

  C x() {
    class C {}
    return new<caret> C();
  }
}