package objects_require_non_null;

class Two {
  private Object o;

  Two(Object o) {
    if (o == null) {
      throw new NullPointerException();
    }
    this.o = <caret>o;
  }
}