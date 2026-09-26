import java.io.File;

final class MyClass {
  
  
  void test(Point p) {
    if (p.getX() == p.getY()) {
      unknown();
      if (<warning descr="Condition 'p.getX() == p.getY()' is always 'true'">p.getX() == p.getY()</warning>) {}
    }
  }
  
  native void unknown();
  
  void testFile(File f) {
    if (!f.exists()) {
      unknown();
      if (!f.exists()) {}
    }
  }
  
  final class Point {
    final int x;
    final int y;
    
    Point(int x, int y) {
      this.x = x;
      this.y = y;
    }
    
    public int getX() {return x;}
    public int getY() {return y;}
  }
}