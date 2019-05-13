class A {
   private int value = 1;

   static class B<T extends A> {
      void print(T t) {
         System.out.println(t.<error descr="'value' has private access in 'A'">value</error>);
      }
   }
}

abstract class Foo<T extends Foo<T>> {
    private int field;

    public int bar(T t){
        return t.<error descr="'field' has private access in 'Foo'">field</error>;
    }
}

class Bug {
  // Idea incorrectly analyses this code with JDK 7
  public <T extends Bug> void doit(T other) {
    // Oops, was legal with JDK 6, no longer legal with JDK 7
    other.<error descr="'mPrivate()' has private access in 'Bug'">mPrivate</error>();
    // Redundant with JDK 6, not a redundant cast with JDK 7
    ((Bug)other).mPrivate();
  }

  // Idea correctly analyses this code
  public void doit2(SubClass other) {
    // Not legal with JDK 6 or 7
    other.<error descr="'mPrivate()' has private access in 'Bug'">mPrivate</error>();
    // Not redundant with JDK 6 or 7
    ((Bug)other).mPrivate();
  }

  private void mPrivate() {
  }
}

class SubClass extends Bug {
}

class A67678
{
      private void foo(){}
      <T extends A67678 & Cloneable> void bar(T x)
      {
          x.<error descr="'foo()' has private access in 'A67678'">foo</error>();
      }
}