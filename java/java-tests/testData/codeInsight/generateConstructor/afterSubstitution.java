class Parent<T> {
  T field;

  public Parent(T field) {
    this.field = field;
  }
}

class Child<Integer> extends Parent<Integer> {
    Child(Integer field) {
        super(field);
    }
}
