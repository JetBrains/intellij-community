class C {
  private static void fn(boolean condA, boolean condB) {
    if (condA) {<caret>
      if (condB) {
        System.out.println("condA && condB");
      }
    } else {
      System.out.println("!condA");
    }
  }
}