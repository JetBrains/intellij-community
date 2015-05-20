class Clazz {
  void callee() {}
}

class User {
  void caller(Clazz clazz) {
    clazz.callee();
  }
}