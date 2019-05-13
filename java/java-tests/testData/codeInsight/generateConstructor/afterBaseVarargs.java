class Base {
  public Base(String... ignored) { }
}
class Derived extends Base {
  int i;

    public Derived(int i, String... ignored) {
        super(ignored);
        this.i = i;
    }
}