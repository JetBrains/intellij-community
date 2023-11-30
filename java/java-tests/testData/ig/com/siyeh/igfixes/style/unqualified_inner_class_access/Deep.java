package unqualified_inner_class_access;

import unqualified_inner_class_access.Deep.One.Two.Three;

class Deep {
  public static class One {
    public static class Two {
      public static class Three {}
    }
  }
}
class User {
  public static void main(String[] args) {
    new <caret>Three();
  }
}