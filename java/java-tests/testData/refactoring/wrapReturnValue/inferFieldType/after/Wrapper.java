class Wrapper<T> {
  T myField;
  Wrapper(T s) {
    myField = s;
  }

  String getMyField() {
    return myField;
  }
}