// "Make 'Point.x' not final" "false"
public record Point(int x, int y, long depth) {

  public void setX(int x) {
    this.<caret>x = x;
  }
}