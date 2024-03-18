import org.jetbrains.annotations.Nullable;

class Test {
  void foo() {
      final String str = newMethod();
      if (str == null) return;
      new Runnable() {
      public void run() {
        System.out.println(str);
      }
    }
  }

    private @Nullable String newMethod() {
        final String str = "";
        if (str == "a") {
            return null;
        }
        return str;
    }
}