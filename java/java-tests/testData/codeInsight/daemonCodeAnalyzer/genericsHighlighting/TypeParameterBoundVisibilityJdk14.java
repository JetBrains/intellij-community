class A {
   private int value = 1;

   static class B<T extends A> {
      void print(T t) {
         System.out.println(t.value);
      }
   }
}

class Bug {
  // Idea incorrectly analyses this code with JDK 7
  public <T extends Bug> void doit(T other) {
    // Oops, was legal with JDK 6, no longer legal with JDK 7
    other.mPrivate();
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