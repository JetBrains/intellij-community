class Simple {
  void foo(Simple s) {
    final Class<? extends Simple> aClass = getClass();
    final Class<? extends Simple> aClass1 = s.getClass();
    final Class<? extends String> nonConflictStrClass = "".getClass();
  }
}

class Usage {
  Simple s = new Sim<caret>ple();
}