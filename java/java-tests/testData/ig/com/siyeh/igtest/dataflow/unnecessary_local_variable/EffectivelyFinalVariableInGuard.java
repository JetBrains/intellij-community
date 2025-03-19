class EffectivelyFinalVariableInGuard {
  public static void main(String[] args) {
    test(new SomeGroup.First());
  }

  sealed interface SomeGroup {
    record First() implements SomeGroup {
    }

    record Second(String text) implements SomeGroup {
    }
  }

  static void test(SomeGroup someGroup) {
    for (int i = 0; i < 5; i++) {
      // used to be effectively final
      int finalI = i;

      switch (someGroup) {
        case SomeGroup.First() when finalI == 0 -> System.out.println("first");
        case SomeGroup.Second(String text) -> System.out.println("second " + text);
        default -> System.out.println("other");
      }
    }
  }
}