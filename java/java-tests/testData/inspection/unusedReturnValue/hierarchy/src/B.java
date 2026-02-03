class B implements I {
  public boolean isUnused(){
    return false;
  }

  public static void main(String[] args) {
    I i = new B();
    i.isUnused();
  }
}