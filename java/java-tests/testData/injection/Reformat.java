class T {
  void doTest(@org.intellij.lang.annotations.Language("JAVA") String code) {}
  void foo() {
    doTest("class LocalAnnotation {" +
           "  @Z Integer i;strictfp" +
           "  @Z Object x() {<caret>" +
           "    @Z Object s = \"\";" +
           "    try {" +
           "      System.out.println((@Z String)s);" +
           "    } catch (@Z RuntimeException e) {}" +
           "    return s;" +
           "  }" +
           "}" +
           "@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) " +
           "@java.lang.annotation.Target(java.lang.annotation.  ElementType.TYPE_USE) " +
           "@interface Z {}");
  }
}