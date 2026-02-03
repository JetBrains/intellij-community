public class JavaClass {
  {
    Outer.Boo b = new Outer.Boo();<caret>
  }
}

class Outer {
  static class Boo { }
}


class ABooImpl extends Outer.Boo { }