class InLoopSingleLineDeclaration {

  void m() {
    for(int i = 0; i < 10; i++)try {
      String fallacy;
    } catch (Exception <caret>ignore) { }
  }
}