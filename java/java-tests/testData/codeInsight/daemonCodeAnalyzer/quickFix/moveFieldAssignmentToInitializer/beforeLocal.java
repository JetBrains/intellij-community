// "Move assignment to field declaration" "false"
public class X {
    int i;
    X(int from)
    {
      <caret>i=from;
    }
}
