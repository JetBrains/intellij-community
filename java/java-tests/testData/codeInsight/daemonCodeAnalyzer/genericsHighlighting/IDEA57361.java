
class C<T> {
  class D {}
  <T extends C<String>.D> void foo(){
    this.<<error descr="Type parameter 'C.D' is not within its bound; should extend 'C<java.lang.String>.D'">D</error>>foo();
  }
}