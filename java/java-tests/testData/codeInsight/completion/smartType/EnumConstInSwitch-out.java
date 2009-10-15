enum E {
  CONS;
  
  void foo (E e) {
    switch (e) {
      case CONS:<caret>
    }
  }
}