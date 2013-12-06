class For {
  void f(List<String> list) {
    for (Iterator<String>  it = list.iterator(); it.hasNext();) {
    }
    for (int i = 0; i < 10; i++) {}
    for (int i = 0, length = 10; i < length; i++) {}
  }
}