// "Remove 'CloneDoesntCallSuperClone' suppression" "false"

class NoSuperCall {
  @SuppressWarnings("CloneDoesntCa<caret>llSuperClone")
  @Override
  public Object clone() throws CloneNotSupportedException {
    return null;
  }

}