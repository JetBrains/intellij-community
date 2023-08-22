class X {
  X(String[] args) {}
  
  X(int x) {
    this(new String[]{});
  }
}
class Y extends X {
    Y() {
        super(new String[]{});
    }
}