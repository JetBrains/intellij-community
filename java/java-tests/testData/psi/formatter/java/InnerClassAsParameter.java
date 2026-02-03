class Foo{
public void foo(){
bar_call(new Runnable(){
void run(){
bar();
bar();
}
});
}
}