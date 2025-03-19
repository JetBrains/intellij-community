class Used {
    Inner createInner() {
        return new Inner();
    }

    class Inner {
        private Inner() {
        }
    }
  public static void main(String[] args) {
      /*2*/
      /*3*/
      /*4*/
      /*5*/
      new /*1*/Used().createInner(/*6*/)/*7*/;
  }
}