public class Aaaaaaa {
  int color;

  void setColor(int color) {}
  int getColor() {}
  int getZooColor() {}

  void foo(Aaaaaaa a) {
    setColor(a.<caret>);
  }


}
