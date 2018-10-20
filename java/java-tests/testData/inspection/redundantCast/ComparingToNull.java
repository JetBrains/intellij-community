class Test {
   public boolean foo() {
        Object o = null;
        return (<warning descr="Casting 'o' to 'String' is redundant">String</warning>) o == null;
    }
}