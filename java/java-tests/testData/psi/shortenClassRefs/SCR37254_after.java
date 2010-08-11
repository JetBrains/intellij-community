class Parent{

    protected Child createChild() {
        return null;
    }

    static class Child {
    }
}

class SubClass extends Parent {
     //Do not shorten Parent.Child to Child
     protected Parent.Child createChild() {
          return null;
     }

    class Child extends Parent.Child {}
}
