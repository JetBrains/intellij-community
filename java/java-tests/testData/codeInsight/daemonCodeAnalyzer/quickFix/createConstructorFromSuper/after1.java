// "Create constructor in 'Base'" "true"
class Base {
    public Base(int i, Object o) {
        
    }
}

public class Derived extends Base {
  Derived () {
    super (1, new Object ());
  }
}
