class Entry {
  private final Object obj;
  protected Entry(Directory parent) {
    obj = ((Entry)parent).obj; //cast is needed because 'obj' is not visible with 'Directory' access class
    String s = ((<warning descr="Casting '((Entry)(Entry)parent).obj' to 'Object' is redundant">Object</warning>)((Entry)(<warning descr="Casting 'parent' to 'Entry' is redundant">Entry</warning>)parent).obj).toString();
  }
}

class Directory extends Entry {
  public Directory(Directory parent) {
    super(parent);
  }
}