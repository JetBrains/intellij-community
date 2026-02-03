class Zoo2 {
  void foo(java.util.List[] dealsInfo) {
    final int dealsNumber = dealsInfo != null && dealsInfo.length == 2 ? dealsInfo[0].size() : 0;
    if (dealsNumber > 0) {
      System.out.println(dealsInfo[0].get(0));
    }
  }
}
