// "Remove 'CloneDoesntCallSuperClone' suppression" "true"

class NoSuperCall {
  @SuppressWarnings("CloneDoesntCa<caret>llSuperClone")
  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

}