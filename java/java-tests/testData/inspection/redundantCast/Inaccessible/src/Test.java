class Entry {
  private final Object obj;
  protected Entry(Directory parent) {
    obj = ((Entry)parent).obj; //cast is needed because 'obj' is not visible with 'Directory' access class
  }
}

class Directory extends Entry {
  public Directory(Directory parent) {
    super(parent);
  }
}