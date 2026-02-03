public abstract class Zzza {

    abstract Object getFoo();

    void foo(Zzza other) {
      int a;
      if (other.getFoo() instanceof String) {
        a = 1;
      } else {
        a = 2;
      }
      other.getFoo().subst<caret>
    }


}