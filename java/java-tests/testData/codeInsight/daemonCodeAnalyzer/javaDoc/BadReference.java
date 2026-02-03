class Test {
  /**
   * This element was written by {@link Test#write(Object, <error descr="Cannot resolve symbol 'XmlWriter'">XmlWriter</error>)}
   * method. So <code>read</code> and <code>write</code> methods should be consistent.
   */
  public void read() {}
  public void write(Object o, <error descr="Cannot resolve symbol 'XmlWriter'">XmlWriter</error> writer) {}
}