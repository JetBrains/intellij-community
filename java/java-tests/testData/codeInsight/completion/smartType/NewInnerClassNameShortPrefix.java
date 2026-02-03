public class JavaClass {
  {
    Outer.Boo b = new B<caret>
  }
}

class Outer {
  static class Boo { }
}


class ABooImpl extends Outer.Boo { }