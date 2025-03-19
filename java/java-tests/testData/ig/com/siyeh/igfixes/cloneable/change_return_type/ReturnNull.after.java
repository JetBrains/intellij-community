class ReturnNull implements Cloneable {
  public ReturnNull clone() {
    try {
        return (ReturnNull) super.clone();
    } catch (CloneNotSupportedException e) {
      return null;
    }
  }
}