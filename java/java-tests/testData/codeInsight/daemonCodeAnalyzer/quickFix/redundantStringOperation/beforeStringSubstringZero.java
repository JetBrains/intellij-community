// "Fix all 'Redundant 'String' operation' problems in file" "true"
class Foo {
  void test(String s) {
    String s1 = s.substring(1);
    // check merger
    //noinspection SubstringZero
    String sOld = s.substring(0x0);
    //noinspection RedundantStringOperation
    String sOld2 = s.substring(0x0);
    //noinspection StringOperationCanBeSimplified
    String sNew = s.substring(0x0);
    String s2 = s.su<caret>bstring(0x0);
    String s3 = s.substring(0, 20);
    String s4 = s.substring(0, /*up until the string length*/ s.length());
    String s5 = s.substring(0, s.length()-1);
  }
}