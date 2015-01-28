class Clazz {
  void callee(Clazz cl<caret>azz) {}
}

class User {
  void caller(Clazz clazz) {
    clazz.callee(clazz);
  }
}