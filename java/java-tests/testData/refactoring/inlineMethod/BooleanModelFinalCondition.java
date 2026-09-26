
class Test {

  private boolean test(String s) {
    if (s == null) return false;
    s = s.trim();
    if (s.isEmpty()) return false;
    int i;
    try {
      i = Integer.parseInt(s);
    }
    catch (NumberFormatException ex) {
      return false;
    }
    return i > 0;
  }

  String use(String[] list) {
    for(String str : list) {
      if (!<caret>test(str)) break;
      System.out.println("Ok string: "+str);
    }
  }
}