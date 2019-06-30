class A {
  {
    List<Integer> l = new ArrayList<Integer>();
    for (Integer i : l) {
        final String toStr = i.toString();
        System.out.println(toStr);
    }
    for (Integer i : l) {
      System.out.println(i.toString());
    }
  }
}