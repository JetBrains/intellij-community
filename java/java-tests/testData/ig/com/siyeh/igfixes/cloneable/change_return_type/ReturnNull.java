class ReturnNull implements Cloneable {
  public Object<caret> clone() {
    try {
      return super.clone();
    } catch (CloneNotSupportedException e) {
      return null;
    }
  }
}