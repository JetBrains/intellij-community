// "Make 'main' not static" "false"
class DateFormat {

  public void parse(String s){}
}
class TestParse {
    public static void main(String[] args) {
      DateFormat.<caret>parse("20070724T174800");
    }
}
