class Comment {
  void x() {
    while (true) {
      int x = new Random().nextInt();
      if<caret> (x == 1) {
        System.out.println();
        break; //comment
      }
      else if (x == 2) {
        System.out.println();
      }
      else if (x == 3) {
        System.out.println();
      }
    }
  }
}