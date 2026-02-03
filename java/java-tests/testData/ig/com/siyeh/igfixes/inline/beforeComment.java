// "Inline variable" "true-preview"
class Comment {
  
  Object x(String s) {
    Object o<caret> = s; // abc
    return o;
  }
}