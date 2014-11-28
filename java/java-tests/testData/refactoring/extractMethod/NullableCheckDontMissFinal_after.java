import org.jetbrains.annotations.NotNull;

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

    @NotNull
    private String newMethod() {
        final String str = "";
        if (str == "") {
            return null;
        }
        return str;
    }
}