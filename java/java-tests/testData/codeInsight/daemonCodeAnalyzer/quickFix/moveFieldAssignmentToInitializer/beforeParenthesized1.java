// "Move assignment to field declaration" "false"
public class X {
  int i;
  {
    <caret>i=(i)=0; // don't throw exception
  }
}
