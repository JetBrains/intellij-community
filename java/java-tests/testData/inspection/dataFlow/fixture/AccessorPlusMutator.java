class X {

  private Object object = null;

  void foo() {
    Object tmp = getObject();
    if (tmp == null) {
      fill(); // object is initialized here
      tmp = getObject();  // no longer null
      if (tmp == null) { // false "condition 'tmp == null' is always 'true'" report
        System.out.println(tmp);
      }
    }

  }

  public Object getObject() {
    return object;
  }

  public void fill() {
  }
}