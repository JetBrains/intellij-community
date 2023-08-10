class Simple implements Cloneable {

  public <caret>Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
}