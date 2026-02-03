class NonPublicClone implements Cloneable {

  @Override
  protected NonPublicClone <warning descr="'clone()' method not 'public'">clone</warning>() throws CloneNotSupportedException {
    return (NonPublicClone)super.clone();
  }
}
class Other implements Cloneable {
  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
}
class Third {

  @Override
  public Third clone() throws CloneNotSupportedException {
    return (Third)super.clone();
  }
}