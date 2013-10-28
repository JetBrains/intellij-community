class For {
  void f(List<String> list) {
    for (Iterator<String>  it = list.iterator(); it.hasNext();) { // 'it' can be final but not reported
    }
    for (int i = 0; i < 10; i++) {}
  }
}