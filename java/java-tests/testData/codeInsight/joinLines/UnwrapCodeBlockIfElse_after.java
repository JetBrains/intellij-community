class C {
  private static void fn(boolean condA, boolean condB) {
    if (condA) { if (condB) {
        System.out.println("condA && condB");
      }
    } else {
      System.out.println("!condA");
    }
  }
}