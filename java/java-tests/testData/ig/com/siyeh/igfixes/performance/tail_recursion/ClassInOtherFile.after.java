class ClassInOtherFile {
  private final Container parent;
  private boolean boundsValid;

  public Visual(Container parent) {
    this.parent = parent;
  }

  void invalidate() {
      ClassInOtherFile other = this;
      while (true) {
          other.boundsValid = false;
          if (other.parent != null) {
              other = other.parent;
              continue;
          }
          return;
      }
  }
}