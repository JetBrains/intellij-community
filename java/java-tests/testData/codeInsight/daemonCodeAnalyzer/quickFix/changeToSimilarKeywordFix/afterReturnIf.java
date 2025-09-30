// "Fix the typo 'retur' to 'return'" "true-preview"
class Test {
  static String test(String a) {
    if(a.length()==1) return test()
  }
}
