import org.jetbrains.annotations.Nullable;

class X {
  static String guessTestDataName(String method, String testName, String[] methods) {
    for (String psiMethod : methods) {
        String strings = newMethod(method, testName);
        if (strings != null) return strings;

    }
    return null;
  }

    @Nullable
    private static String newMethod(String method, String testName) {
        String strings = method;
        if (strings != null && !strings.isEmpty()) {
          return strings.substring(0) + testName;
        }
        return null;
    }
}
