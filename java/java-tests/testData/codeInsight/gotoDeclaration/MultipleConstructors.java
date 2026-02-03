import java.lang.String;

class X {
  X(int i){}
  X(String s){}
  {
    new <caret>X();
  }
}