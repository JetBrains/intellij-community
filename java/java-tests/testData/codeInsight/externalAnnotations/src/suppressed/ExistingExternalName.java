package suppressed;
class ExistingExternalName {
  String foo(){
    String str = "";
    s<caret>tr = str;
    return str;
  }
}