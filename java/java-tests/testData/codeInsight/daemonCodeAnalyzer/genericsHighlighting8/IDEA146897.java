
interface XYZ<<error descr="Cyclic inheritance involving 'X'"></error>X extends X> {

  class Q {
    public static void main(String[] args) {
      <error descr="'XYZ' is abstract; cannot be instantiated">new XYZ<>()</error>;
    }
  }
}