class Test {
  public void testContinue() {
    Object o = null;
    for (int i = 0; i < 4; i++) {
      try {
        if (o == null) {
          System.out.println("hello");
          continue;
        }
        System.out.println("fred");
      } finally {
        o = "";
      }
    }
  }

  public void testBreak() {
    Object o = null;
    while (true) {
      try {
        System.out.println("hello");
        break;
      } finally {
        o = "";
      }
    }
    if (<warning descr="Condition 'o != null' is always 'true'">o != null</warning>) {
      System.out.println("fred");
    }
  }
}