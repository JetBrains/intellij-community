// "Rename reference" "true-preview"
class c {
 void foo(boolean b) {
   if (b) {
     int i = 0;
     i++;
   } else {
     i<caret>++;
   }
 }
 int k;
}