// "Suppress for statement" "true"
class a {
  public void aa(){
     int a = 0;
      //noinspection SillyAssignment
      a = <caret>a;
  }
}
