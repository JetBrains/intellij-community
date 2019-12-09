// "Remove 'CloneDoesntCallSuperClone' suppression" "true"

class NoSuperCall {
  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

}