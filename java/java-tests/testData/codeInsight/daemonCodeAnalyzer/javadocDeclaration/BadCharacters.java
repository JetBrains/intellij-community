import java.util.*;

class Test<T> {
  public void read(List<T> list) {}

  /**
   * @see #read(java.util.List<warning descr="Illegal character"><</warning>T<warning descr="Illegal character">></warning>)
   */
  public void write() {}
}