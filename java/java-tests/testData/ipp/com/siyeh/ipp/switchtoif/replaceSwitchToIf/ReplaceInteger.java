class T {
  void foo(Integer i) {
    sw<caret>itch (i) {
      case 0:
        System.out.println(i);
    }
  }
}