class Clazz {
  void doSmth(Child child) {
    child.execute();
  }

  public static void main(String[] args) {
    new Clazz().doSmth(() -> {});
  }
}

interface Parent {
  void execute();
}

interface Child extends Parent {
  void execute();
}