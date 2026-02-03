
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

class Temp {
  private static String[] getMemberClass(String containingClass) {
    return bar(new Supplier<String[]>() {
      @Override
      public String[] get() {
        if (containingClass != null) {
          List<String> result = new ArrayList<>();
          foo();
          //if (psiClass != null) return psiClass;
        }
        return null;
      }

      void foo() {}
    });
  }

  private static String[] bar(Supplier<String[]> supplier) {
    return new String[0];
  }
}