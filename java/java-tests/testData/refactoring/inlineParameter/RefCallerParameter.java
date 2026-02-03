class Outer {
  public void withClass(Object <caret>o) {
    System.out.println(o.toString());
  }

  public void foor(Object objct) {
    withClass(objct);
  }
}
