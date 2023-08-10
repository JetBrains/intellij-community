class C {
  private static void fn(boolean condA, boolean condB) {
    if (condA) {<caret>
      for(int i=0; i<10; i++)
        if (condB) {
          System.out.println("condA && condB");
        }
    } else {
      System.out.println("!condA");
    }
  }
}