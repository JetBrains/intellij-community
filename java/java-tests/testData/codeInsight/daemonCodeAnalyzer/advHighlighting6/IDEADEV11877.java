interface MyCloneable {

    //protected method from java.lang.Object is not implicitly declared in interface with no base interfaces
    int clone();

    <error descr="'toString()' in 'MyCloneable' clashes with 'toString()' in 'java.lang.Object'; attempting to use incompatible return type">int</error> toString();
}

interface MyCloneable1 {
  <error descr="Method does not override method from its superclass">@Override</error>
  Object clone();
}

class MyCloneable2 {
  @Override
  public Object clone() {
    return null;
  }
}

interface MyFinalizable {
  <error descr="Method does not override method from its superclass">@Override</error> void finalize();
  @Override int hashCode();
}