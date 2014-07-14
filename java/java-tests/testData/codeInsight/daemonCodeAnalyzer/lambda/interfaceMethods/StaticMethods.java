interface A
{
  static void foo(){}
}
interface B extends A
{
  static int foo(){ return 1; }
}