// "Suppress for field" "false"
/** @noinspection ALL*/
class a {
 static private String <caret>mm = "00";
  // The "Convert to local" inspection should be reported here if not suppressed

 static void test() {
  mm = "1";
  if(mm == "1") {
   mm = "2";
  }
 }
}