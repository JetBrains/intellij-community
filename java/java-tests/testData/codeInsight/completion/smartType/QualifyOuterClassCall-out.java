interface I {
  int getFoo();
}
class Outer {
  private final I myInner = new I() {
    @Override
    public int getFoo() {
      return Outer.this.getFoo();<caret>
    }
  };

  int getFoo() { return 0; }
}