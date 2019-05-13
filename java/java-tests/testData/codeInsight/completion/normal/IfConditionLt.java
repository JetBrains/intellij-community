class Zoo {

  void foo(int x) {
    if (x < getAnnotati<caret>onsAreaOffset());

  }

  int getAnnotationsAreaOffset() {}
}
