class Boxed {
  boolean foo() {
    Object isContainer = Math.random() > 0.5 ? new Object() : null;
    if (isContainer != null) {
      // We don't want to suggest any quick fix here because 'isContainer' is not a Boolean
      return Boolean.TRUE.equals(isContainer);
    }
    return false;
  }
}
