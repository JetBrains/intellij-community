class NoBraces {
  void m() {
      //after while
      //before body
      if (b(/*inside call*/)) {
          do System.out.println();
          while (b(/*inside call*/));
      }
  }

  boolean b() {
    return true;
  }
}