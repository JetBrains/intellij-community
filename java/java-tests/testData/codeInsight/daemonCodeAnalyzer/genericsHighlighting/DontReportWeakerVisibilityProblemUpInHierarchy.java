
class F {
  public void f() {}
}

class E extends F {
  @Override
  <error descr="'f()' in 'E' clashes with 'f()' in 'F'; attempting to assign weaker access privileges ('protected'); was 'public'">protected</error> void f() {
    super.f();
  }
}

class EE extends E {

}