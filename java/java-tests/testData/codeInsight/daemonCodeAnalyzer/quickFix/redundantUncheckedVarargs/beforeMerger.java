// "Remove 'CloneDoesntCallSuperClone' suppression" "true-preview"

class NoSuperCall {
  @SuppressWarnings("CloneDoesntCa<caret>llSuperClone")
  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

}