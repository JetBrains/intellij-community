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
    <error descr="Unreachable statement">if</error> (b == true) {
      System.out.println("Never");
    }
    else {
      System.out.println("Never ever");
    }
  }

  void testIf3(boolean b) {
    return;
    <error descr="Unreachable statement">if</error> ((b)) {
      System.out.println("Never");
    }
  }

  void testIf4() {
    return;
    <error descr="Unreachable statement">if</error> (Math.abs(1) > 2) {
      System.out.println();
    }
  }

  void testIf5() {
    return;
    <error descr="Unreachable statement">if</error> (new int[] {1,2,3}.length == 3) {
      System.out.println();
    }
  }

  void testIf6(boolean b, boolean c, boolean d) {
    return;
    <error descr="Unreachable statement">if</error> (b ? c : d) {
      System.out.println();
    }
  }

  void testSwitch(int b) {
    return;
    <error descr="Unreachable statement">switch</error> (b) {
      case 1:
        System.out.println("Never");
    }
  }

  void testSwitch2(int b) {
    return;
    <error descr="Unreachable statement">switch</error> (b+1) {
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

  void testWhile2(boolean b) {
    return;
    <error descr="Unreachable statement">while</error> (!b) {
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

  void testDoWhile3(boolean b) {
    return;
    <error descr="Unreachable statement">do</error> {
      if(b) {
        System.out.println("x");
      }
    } while(true);
  }

  void testDoWhile4() {
    return;
    <error descr="Unreachable statement">do</error> do do do {
      System.out.println("pchela letela");
    } while(true); while(true); while(true); while(true);
  }

  void testIfLabeled(boolean b) {
    return;
    <error descr="Unreachable statement">LABEL</error>:
    if (b == true) {
      break LABEL;
    }
  }

  void testDoIfLabeled(boolean b) {
    return;
    <error descr="Unreachable statement">do</error> {
      LABEL:
      if (b == true) {
        break LABEL;
      }
    } while(true);
  }

  int testReturn() {
    return 1;
    <error descr="Unreachable statement">return</error> 2;
  }
}