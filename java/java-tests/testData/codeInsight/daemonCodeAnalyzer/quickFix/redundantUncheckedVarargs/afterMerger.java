// "Remove 'CloneDoesntCallSuperClone' suppression" "true-preview"

class NoSuperCall {
  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

}