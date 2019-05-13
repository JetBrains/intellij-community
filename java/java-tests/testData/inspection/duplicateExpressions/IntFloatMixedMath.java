class C {
  void foo(int x, int y, int h, float t) {
    bar(x, <weak_warning descr="Multiple occurrences of 'y + h / 2 - t * 2'">y + h / 2 - t * 2</weak_warning>, x, <weak_warning descr="Multiple occurrences of 'y + h / 2 - t * 2'">y + h / 2 - t * 2</weak_warning> + t / 20);
    bar(x, y + h / 2 + t * 2, x, y + h / 2 + t * 2 + t / 20); // can't split polyadic expressions, unfortunately
  }

  void bar(float x1, float y1, float x2, float y2){}
}