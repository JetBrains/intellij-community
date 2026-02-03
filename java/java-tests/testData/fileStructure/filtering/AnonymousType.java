import java.awt.*;

class AnonymousType<caret> {
  int num;
  void foo() {
    new java.awt.Point(){};
  }
}