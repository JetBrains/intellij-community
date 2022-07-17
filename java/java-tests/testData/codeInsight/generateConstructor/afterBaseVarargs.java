class Base {
  public Base(String... ignored) { }
}
class Derived extends Base {
  int i;

    Derived(int i, String... ignored) {
        super(ignored);
        this.i = i;
    }
}