// "Create constructor in 'Base'" "true"
class Base {
}

public class Derived extends Base {
  Derived () {
    su<caret>per (1, new Object ());
  }
}
