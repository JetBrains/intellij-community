// "Move initializer to constructor" "false"
public class X {
  {

new Runnable() {
 int <caret>i=0;
 public void run() {
 }
}.run();
  }
}
