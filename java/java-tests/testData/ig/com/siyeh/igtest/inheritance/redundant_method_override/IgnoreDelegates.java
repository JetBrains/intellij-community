class ParentClass {
  void foo(int x, int y){
    System.out.println(x + y);
  }
}

class ChildForClass extends ParentClass {
  @Override
  void <warning descr="Method 'foo()' is identical to its super method">foo</warning>(int x, int y) {
    System.out.println(x + y);
  }
}

class ChildWithSuperForClass extends ParentClass {
  @Override
  void foo(int x, int y) {
    super.foo(x, y);
  }
}

interface ParentInterface {
  default void foo(int x, int y){
    System.out.println(x + y);
  }
}

class ChildForInterface implements ParentInterface {
  public void <warning descr="Method 'foo()' is identical to its super method">foo</warning>(int x, int y){
    System.out.println(x + y);
  }
}

class ChildWithSuperForInterface implements ParentInterface {
  public void foo(int x, int y){
    ParentInterface.super.foo(x, y);
  }
}