// "Suppress for statement" "true"
class a {
static void setA(){
}
void b(){
    //noinspection AccessStaticViaInstance
    <caret>new a().setA();
}
}
