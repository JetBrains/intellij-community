
class TableModelBuilder<Self extends TableModelBuilder<?>> {
  public Self addColumns() {
    return null;
  }

  public Self addAndGet() {
    return null;
  }

  public void leave() {}

  void foo(TableModelBuilder<?> m) {
    m.addColumns().addAndGet() .leave();
  }
}
