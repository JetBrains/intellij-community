// "Suppress for statement" "true"
class a {
static void setA(){
}
void b(){
  <caret>new a().setA();
}
}
