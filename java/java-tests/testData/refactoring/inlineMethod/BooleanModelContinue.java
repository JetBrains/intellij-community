
class Test {

  private boolean test(String s) {
    if(s != null) {
      s = s.trim();
      if (s.isEmpty()) {
        return true;
      }
    }
    return false;
  }

  String use(String[] list) {
    for(String str : list) {
      if (<caret>test(str)) continue;
      System.out.println("Ok string: "+str);
    }
  }
}