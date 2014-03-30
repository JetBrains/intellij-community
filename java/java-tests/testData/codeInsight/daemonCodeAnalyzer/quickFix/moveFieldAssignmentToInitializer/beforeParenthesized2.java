// "Move assignment to field declaration" "true"
public class X {
  int i;
  {
    (i)=<caret>0;
  }
}
