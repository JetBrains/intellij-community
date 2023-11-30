class Nested implements Cloneable {

  public <caret>Object clone() throws CloneNotSupportedException {
    new Object() {
      Object x() {
        return null;
      }
    }
    return /*1*/ super.clone();
  }
}