class A {
  {
    List<Integer> l = new ArrayList<Integer>();
    for (Integer i : l) {
        final String tostr = i.toString();
        System.out.println(tostr);
    }
    for (Integer i : l) {
      System.out.println(i.toString());
    }
  }
}