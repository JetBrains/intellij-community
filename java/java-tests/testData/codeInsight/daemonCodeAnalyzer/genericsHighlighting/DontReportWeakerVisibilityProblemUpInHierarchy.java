
class F {
  public void f() {}
}

class E extends F {
  @Override
  <error descr="'f()' in 'E' clashes with 'f()' in 'F'; cannot reduce visibility from 'public' to 'protected'">protected</error> void f() {
    super.f();
  }
}

class EE extends E {

}