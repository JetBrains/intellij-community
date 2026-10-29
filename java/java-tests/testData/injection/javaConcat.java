class C {
  void a() {
    b("void x() {" +
     <caret>           "  new String();" +
                "}"
                );
  }

  void b(@org.intellij.lang.annotations.Language(value = "JAVA") String s) {}

}
