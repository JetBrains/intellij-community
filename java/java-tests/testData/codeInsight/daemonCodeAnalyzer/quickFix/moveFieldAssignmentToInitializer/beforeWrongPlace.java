// "Move assignment to field declaration" "false"
public class X {
    int <caret>i=7;
    {
      i=0;
    }
}
