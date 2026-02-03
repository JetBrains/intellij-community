import org.checkerframework.checker.tainting.qual.Untainted;

class LocalCheck {


  public enum State {
    OFF, ON
  }

  public @interface InterfaceSomething {

  }

  void test(@Untainted String clean, String dirty, State state, InterfaceSomething interfaceSomething) {
    sink(clean);
    sink(<warning descr="Unknown string is used as safe parameter">dirty</warning>); //warn
    sink(state.name());
    sink(interfaceSomething.toString());
  }

  void sink(@Untainted String clean) {

  }
}
