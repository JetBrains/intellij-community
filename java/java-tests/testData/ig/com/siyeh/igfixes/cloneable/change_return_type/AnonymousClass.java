class AnonymousClass {

  {
    B settings = new B() {
      public AnonymousClass<caret> clone() throws CloneNotSupportedException {
        return (AnonymousClass)super.clone();
      }

    };
  }

}

class B implements Cloneable {

}