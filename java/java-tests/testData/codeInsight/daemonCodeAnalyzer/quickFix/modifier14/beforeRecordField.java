// "Make 'Point.x' not final" "false"
public record Point(int x, int y, long depth) {

  public Point(int x, int y) {
    this(x, y, 10);
    this.<caret>x = x;
    this.y = y;
  }
}