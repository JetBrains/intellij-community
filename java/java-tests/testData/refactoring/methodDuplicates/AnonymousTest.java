class AnonymousTest {
  interface Thing {
    boolean thing();
  }

  void dupeHolder() {
    Thing thing = new Thing() {
      public boolean thing() {
        return false;
      }
    };
  }

  Thing <caret>duplicator(final boolean thingReturn) {
    return new Thing() {
      public boolean thing() {
        return thingReturn;
      }
    };
  }
}