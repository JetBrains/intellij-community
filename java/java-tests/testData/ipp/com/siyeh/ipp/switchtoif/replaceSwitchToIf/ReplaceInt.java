class T {
  void foo(int i) {
    sw<caret>itch (i) {
      case 0:
        System.out.println(i);
    }
  }
}