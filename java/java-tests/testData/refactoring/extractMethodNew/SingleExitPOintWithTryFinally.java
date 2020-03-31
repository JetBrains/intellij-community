class C {
  private void foo() {
    System.out.println();
    <selection>try {}
    finally {
      while (true) {
        break;
      }
    }
    </selection>
  }
}