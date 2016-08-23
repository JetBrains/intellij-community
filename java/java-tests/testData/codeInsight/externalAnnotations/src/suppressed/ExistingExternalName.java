package suppressed;
class ExistingExternalName {
  String foo(){
    String str = "";
    str = st<caret>r;
    return str;
  }
}