class Super implements Cloneable {
  @Override
  protected final Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
}

class S<caret>ub extends Super {

}