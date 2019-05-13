class Some {
  void assertContainsAllVariants(boolean b) {
    try {
      assert b;
      System.out.println();
    } catch (Throwable e) {
      if (e instanceof AssertionError) {
        System.out.println();
      }
    }
  }

}


