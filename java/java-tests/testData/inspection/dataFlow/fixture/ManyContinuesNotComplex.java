class Foo {

  void foo(String[] lines) {
    for (String line : lines) {
      if (line.hashCode() == 1) { int a1 = 1; continue; }
      if (line.hashCode() == 2) { int a2 = 1; continue; }
      if (line.hashCode() == 3) { int a3 = 1; continue; }
      if (line.hashCode() == 4) { int a4 = 1; continue; }
      if (line.hashCode() == 5) { int a5 = 1; continue; }
      if (line.hashCode() == 6) { int a6 = 1; continue; }
      if (line.hashCode() == 7) { int a7 = 1; continue; }
      if (line.hashCode() == 8) { int a8 = 1; continue; }
      if (line.hashCode() == 9) { int a9 = 1; continue; }
      if (line.hashCode() == 10) { int a10 = 10; continue; }
    }
  }

}