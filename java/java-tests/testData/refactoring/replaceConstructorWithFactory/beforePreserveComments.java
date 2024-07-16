class Used {
  class Inner<caret> {}
  public static void main(String[] args) {
    new /*1*/Used()/*2*/./*3*/new /*4*/Inner/*5*/(/*6*/)/*7*/;
  }
}