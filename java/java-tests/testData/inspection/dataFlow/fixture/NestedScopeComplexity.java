class NestedScopeComplexity {
  static final Object C1 = get();
  static final Object C2 = get();
  static final Object C3 = get();
  static final Object C4 = get();
  static final Object C5 = get();
  static final Object C6 = get();
  static final Object C7 = get();
  static final Object C8 = get();
  static final Object C9 = get();
  static final Object C10 = get();
  static final Object C11 = get();
  static final Object C12 = get();
  static final Object C13 = get();
  static final Object C14 = get();
  static final Object C15 = get();
  static final Object C16 = get();
  static final Object C17 = get();
  static final Object C18 = get();
  static final Object C19 = get();
  static final Object C20 = get();
  static final Object C21 = get();
  static final Object C22 = get();
  static final Object C23 = get();
  static final Object C24 = get();
  static final Object C25 = get();
  static final Object C26 = get();
  static final Object C27 = get();
  static final Object C100 = get();

  static Object get() {return new Object();}

  void test() {
    int i = 1;
    for(Object obj = get(); obj != C100; obj = get()) {
      if (obj == C1) i = 2;
      if (obj == C2) i = 4;
      if (obj == C3) i = 6;
      if (obj == C4) i = 8;
      if (obj == C5) i = 10;
      if (obj == C6) i = 12;
      if (obj == C7) i = 14;
      if (obj == C8) i = 16;
      if (obj == C9) i = 18;
      if (obj == C10) i = 20;
      if (obj == C11) i = 22;
      if (obj == C12) i = 24;
      if (obj == C13) i = 26;
      if (obj == C14) i = 28;
      if (obj == C15) i = 30;
      if (obj == C16) i = 32;
      if (obj == C17) i = 34;
      if (obj == C18) i = 36;
      if (obj == C19) i = 38;
      if (obj == C20) i = 40;
      if (obj == C21) i = 42;
      if (obj == C22) i = 44;
      final Object o2 = obj;
      Runnable run = new Runnable() {
        // Assert that no "too complex" warning on the "run" method
        public void run() {
          if (o2 == C23 || o2 == C24) {
            System.out.println(o2);
          }
        }
      };
    }
  }
}
