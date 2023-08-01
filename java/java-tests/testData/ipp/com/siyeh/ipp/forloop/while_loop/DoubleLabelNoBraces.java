class DoubleLabelNoBraces {

  void m(int i) {
    while (i > 1)
      while (i < 10)
        a: b: c: for<caret> (int j = 0; j < 10; j++);

  }
}