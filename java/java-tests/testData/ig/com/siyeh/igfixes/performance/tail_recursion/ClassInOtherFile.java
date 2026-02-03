class ClassInOtherFile {
  private final Container parent;
  private boolean boundsValid;

  public Visual(Container parent) {
    this.parent = parent;
  }

  void invalidate() {
    boundsValid = false;
    if (parent != null) {
      parent.<caret>invalidate();
    }
  }
}