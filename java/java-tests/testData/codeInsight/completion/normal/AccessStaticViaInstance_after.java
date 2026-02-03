@SuppressWarnings("AccessStaticViaInstance")
public class KeyVO {
  {
    new Cli().foo();<caret>
  }
}

class Cli {
  static void foo() {}
}
