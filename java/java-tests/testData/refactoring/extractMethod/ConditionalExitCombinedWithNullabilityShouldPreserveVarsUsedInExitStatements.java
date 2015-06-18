class X {
  static String guessTestDataName(String method, String testName, String[] methods) {
    for (String psiMethod : methods) {
      <selection>String strings = method;
      if (strings != null && !strings.isEmpty()) {
        return strings.substring(0) + testName;
      }
      </selection>
    }
    return null;
  }
}
