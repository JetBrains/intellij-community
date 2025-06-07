class C {
  public static void main(String[] args) {
    switch (args.length) {

      <error descr="Statement must be prepended with a case label">return;</error>
      case 1:
        System.out.println("");
        System.out.println("");
        System.out.println("");
        System.out.println("");
      case 2:
        System.out.println("");
        System.out.println("");
        System.out.println("");
        System.out.println("");
    }
  }
}