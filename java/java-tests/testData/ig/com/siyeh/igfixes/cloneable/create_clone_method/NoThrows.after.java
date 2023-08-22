class NoThrows extends Parent implements Cloneable {
    private String[] ss;

    @Override
    public NoThrows clone() {
        NoThrows clone = (NoThrows) super.clone();
        // TODO: copy mutable state here, so the clone can't change the internals of the original
        return clone;
    }
}
class Parent implements Cloneable {

  public Parent clone() {
    try {
      return (Parent) super.clone();
    } catch (CloneNotSupportedException e) {
      throw new AssertionError()
    }
  }
}