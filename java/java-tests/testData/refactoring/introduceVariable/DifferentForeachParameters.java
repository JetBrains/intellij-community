class A {
  {
    List<Integer> l = new ArrayList<Integer>();
    for (Integer i : l) {
      System.out.println(<selection>i.toString()</selection>);
    }
    for (Integer i : l) {
      System.out.println(i.toString());
    }
  }
}