import java.util.*;

class A {
  public static void main(String[] args) {
    int i = 0;
    @SuppressWarnings("unused") int j = 0;
    int k;

    initializeCons(value -> {
        String unusedStr1 = "s";
        @SuppressWarnings("unused") String unusedStr2 = "s";
        {
          String unusedStr3;
        }
        String unusedStr4 = "str";
        initializeCons(innerValue1 -> {
          {
            String unusedStr5 = unusedStr4;
          }
          String unusedStr6 = "s";
          String unusedStr7;
          initializeSup(() -> unusedStr6);

          class Local {
            void test() {
              String unusedStr8;
              String unusedStr9 = "Str";
            }
          }
          new Local().test();

          Local o = new Local() {
            void test() {
              String unusedStr10;
              String unusedStr11 = "Str";
            }
          }
          System.out.println(o);
        });
    });
  }

  private static void initializeCons(Consumer<String> consumer) {
    consumer.accept("String to test");
  }

  private static void initializeSup(Supplier<String> supplier) {
    supplier.get();
  }
}

