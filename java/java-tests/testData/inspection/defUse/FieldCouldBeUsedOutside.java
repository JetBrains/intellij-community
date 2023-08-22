class FieldCouldBeUsedOutside {

  static class CheckCase {
    int notUsed = <warning descr="Variable 'notUsed' initializer '1' is redundant">1</warning>;
    public CheckCase(){
      notUsed = 2;
    }
  }

  static class InnerMethod {
    int useInAnotherMethod = 1;
    public InnerMethod(){
      check();
      useInAnotherMethod = 2;
    }

    private void check() {
      if (useInAnotherMethod == 1) {
        System.out.println("1");
      }
    }
  }

  static class CallWithThis {
    int useInAnotherMethod = 1;

    public CallWithThis() {
      check(this);
      useInAnotherMethod = 2;
    }

    private static void check(CallWithThis innerMethod) {
      if (innerMethod.useInAnotherMethod == 1) {
        System.out.println("1");
      }
    }
  }
}