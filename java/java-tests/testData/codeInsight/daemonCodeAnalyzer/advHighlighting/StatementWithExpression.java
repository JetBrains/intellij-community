class WithCondition {

  void testIf1(boolean b) {
    return;
    <error descr="Unreachable statement">if</error> (b) {
      System.out.println("Never");
    }
    else {
      System.out.println("Never ever");
    }
  }

  void testIf2(boolean b) {
    return;
    <error descr="Unreachable statement">if</error> ((b)) {
      System.out.println("Never");
    }
  }

  void testSwitch(int b) {
    return;
    <error descr="Unreachable statement">switch</error> (b) {
      case 1:
        System.out.println("Never");
    }
  }

  void testFor1(int b) {
    return;
    <error descr="Unreachable statement">for</error> (int i = 0; i < b; i++) {
      System.out.println("Never");
    }
  }

  void testFor2(int b) {
    int i;
    return;
    <error descr="Unreachable statement">for</error> (i = 0; i < b; i++) {
      System.out.println("Never");
    }
  }

  void testFor3(int b) {
    int i = 0;
    return;
    <error descr="Unreachable statement">for</error> (i++; i < b; i++) {
      System.out.println("Never");
    }
  }

  void testFor3(boolean b) {
    return;
    <error descr="Unreachable statement">for</error> (; b; ) {
      System.out.println("Never");
    }
  }

  void testFor4() {
    return;
    <error descr="Unreachable statement">for</error> (; ; ) {
      System.out.println("Never");
    }
  }

  void testFor5() {
    int i = 0;
    return;
    <error descr="Unreachable statement">for</error> (; ; i++) {
      System.out.println("Never");
    }
  }

  void testWhile(boolean b) {
    return;
    <error descr="Unreachable statement">while</error> (b) {
      System.out.println("Never");
    }
  }

  void testDoWhile1(boolean b) {
    return;
    <error descr="Unreachable statement">do</error> System.out.println("Never");
    while (b);
  }

  void testDoWhile2(boolean b) {
    return;
    <error descr="Unreachable statement">do</error> {
      System.out.println("Never");
    }
    while (b);
  }
}