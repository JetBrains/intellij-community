// "Create property" "true"
class JC {
    private boolean foo;

    public boolean isFoo() {<caret>
        return foo;
    }

    public void setFoo(boolean foo) {
        this.foo = foo;
    }
}

class Main {
  void usage(JC jc) {
    jc.isFoo();
  }
}
