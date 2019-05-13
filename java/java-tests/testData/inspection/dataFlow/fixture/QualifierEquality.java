class QualifierEquality {

  public void test(int[][] data, int[][] other) {
    if(data != other) return;
    if(<warning descr="Condition 'data[0].length != other[0].length' is always 'false'">data[0].length != other[0].length</warning>) return;
    System.out.println("ok");
  }

  public void test2(String[] data, String[] other) {
    if(data != other) return;
    String first = data[0];
    String otherFirst = other[0];
    int len = first.length();
    int otherLen = otherFirst.length();
    if(<warning descr="Condition 'len != otherLen' is always 'false'">len != otherLen</warning>) {
      System.out.println("Impossible");
    }
    System.out.println(data+":"+other+":"+first+":"+otherFirst);
  }
}