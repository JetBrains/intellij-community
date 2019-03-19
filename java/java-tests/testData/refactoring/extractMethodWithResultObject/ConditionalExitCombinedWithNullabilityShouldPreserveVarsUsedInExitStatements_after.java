class X {
  static String guessTestDataName(String method, String testName, String[] methods) {
    for (String psiMethod : methods) {
      String strings = method;
      if (strings != null && !strings.isEmpty()) {
        return strings.substring(0) + testName;
      }

    }
    return null;
  }//ins and outs
//in: PsiParameter:method
//in: PsiParameter:testName
//out: INSIDE PsiBinaryExpression:strings.substring(0) + testName
//out: OUTSIDE null
}
