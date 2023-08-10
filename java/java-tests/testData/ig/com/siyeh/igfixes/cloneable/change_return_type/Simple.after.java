class Simple implements Cloneable {

  public Simple clone() throws CloneNotSupportedException {
      return (Simple) super.clone();
  }
}