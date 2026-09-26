import java.lang.String;

class X {
  <S> X(int i){}
  <T> X(String s){}
  {
    new <caret>X
  }
}