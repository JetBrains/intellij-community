class C {
  void foo(boolean b) throws Exception {
    try {
      if (b) {
        throw new ChildException();
      } else {
        method();
      }
    } catch (ChildException e) {
      System.out.println("child");
    } catch (ParentException e) {
      System.out.println("parent");
    }
  }

  private static void method() throws Exception {
    throw new ParentException();
  }

  static class ParentException extends Exception { }

  static class ChildException extends ParentException { }
}