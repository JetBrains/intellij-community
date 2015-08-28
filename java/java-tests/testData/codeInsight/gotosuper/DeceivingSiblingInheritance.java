package z;

interface FileType {
  String getName();
}
abstract class LanguageFileType implements FileType {

}
abstract class OCBaseLanguageFileType extends LanguageFileType {
  public String <caret>getName() {
    return "";
  }
}

public class XibFileType extends OCBaseLanguageFileType implements FileType {

}