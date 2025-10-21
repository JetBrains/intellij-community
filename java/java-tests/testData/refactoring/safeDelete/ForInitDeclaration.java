class C {
  
  void x() {
    for (int deleteMe<caret> = 0, i = 0; i < 10; i++) {}
  }
}